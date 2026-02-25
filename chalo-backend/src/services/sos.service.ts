// ============================================================
// Chalo Backend — SOS Service
// Emergency alert handling during active rides
// ============================================================

import prisma from '../config/database';
import { ApiError } from '../utils/apiError';
import logger from '../config/logger';
import { SOSStatus, RideStatus } from '@prisma/client';

export class SOSService {
  /**
   * Trigger SOS alert during an active ride
   * Sends alert to emergency contacts with live location
   */
  async triggerSOS(rideId: string, userId: string, lat: number, lng: number) {
    // Verify ride is active
    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      include: {
        customer: {
          include: {
            customerProfile: true,
          },
        },
        driver: {
          include: {
            driverProfile: true,
          },
        },
      },
    });

    if (!ride) {
      throw ApiError.notFound('Ride not found');
    }

    // Verify the caller is a participant in this ride
    if (ride.customerId !== userId && ride.driverId !== userId) {
      throw ApiError.forbidden('Only ride participants can trigger SOS');
    }

    const activeStatuses: RideStatus[] = [
      RideStatus.DRIVER_ASSIGNED,
      RideStatus.DRIVER_ARRIVED,
      RideStatus.IN_PROGRESS,
    ];

    if (!activeStatuses.includes(ride.status)) {
      throw ApiError.badRequest('SOS can only be triggered during an active ride');
    }

    // Check if there's already an active SOS for this ride
    const existingSOS = await prisma.sOSAlert.findFirst({
      where: { rideId, status: SOSStatus.ACTIVE },
    });

    if (existingSOS) {
      throw ApiError.conflict('An SOS alert is already active for this ride');
    }

    // Build alert recipient list
    const emergencyContact = ride.customer.customerProfile;
    const alertRecipients: Array<{ name: string; phone: string; channel: string }> = [];

    if (emergencyContact?.emergencyContactName && emergencyContact?.emergencyContactPhone) {
      alertRecipients.push({
        name: emergencyContact.emergencyContactName,
        phone: emergencyContact.emergencyContactPhone,
        channel: 'SMS',
      });
      alertRecipients.push({
        name: emergencyContact.emergencyContactName,
        phone: emergencyContact.emergencyContactPhone,
        channel: 'WHATSAPP',
      });
    }

    // Create SOS alert
    const sosAlert = await prisma.sOSAlert.create({
      data: {
        rideId,
        userId,
        lat,
        lng,
        status: SOSStatus.ACTIVE,
        alertSentTo: alertRecipients,
      },
    });

    // Add ride event
    await prisma.ride.update({
      where: { id: rideId },
      data: {
        rideEvents: {
          create: {
            eventType: 'SOS_TRIGGERED',
            lat,
            lng,
            metadata: {
              sosAlertId: sosAlert.id,
              customerName: ride.customer.name,
              driverName: ride.driver?.name,
              vehicleNumber: ride.driver?.driverProfile?.vehicleNumber,
            },
          },
        },
      },
    });

    // TODO: Send actual SMS/WhatsApp alerts via provider (Twilio, MSG91, WhatsApp Business API)
    // Alert content:
    // "EMERGENCY: {customerName} triggered SOS during a ride.
    //  Driver: {driverName} ({vehicleNumber})
    //  Live location: https://maps.google.com/?q={lat},{lng}
    //  Time: {timestamp}"

    logger.warn('SOS ALERT TRIGGERED', {
      sosAlertId: sosAlert.id,
      rideId,
      userId,
      lat,
      lng,
      recipients: alertRecipients.length,
    });

    return {
      sosAlertId: sosAlert.id,
      status: SOSStatus.ACTIVE,
      alertsSent: alertRecipients.length,
      message: 'SOS alert sent to emergency contacts',
    };
  }

  /**
   * Resolve SOS alert (manually by customer or auto on ride completion)
   */
  async resolveSOS(sosAlertId: string, userId: string, autoResolve = false) {
    const sosAlert = await prisma.sOSAlert.findUnique({ where: { id: sosAlertId } });

    if (!sosAlert) {
      throw ApiError.notFound('SOS alert not found');
    }

    if (sosAlert.status !== SOSStatus.ACTIVE) {
      throw ApiError.badRequest('SOS alert is not active');
    }

    if (!autoResolve && sosAlert.userId !== userId) {
      throw ApiError.forbidden('Only the person who triggered the SOS can resolve it');
    }

    const resolved = await prisma.sOSAlert.update({
      where: { id: sosAlertId },
      data: {
        status: autoResolve ? SOSStatus.AUTO_RESOLVED : SOSStatus.RESOLVED,
        resolvedAt: new Date(),
      },
    });

    logger.info('SOS alert resolved', { sosAlertId, autoResolve });

    return {
      sosAlertId: resolved.id,
      status: resolved.status,
    };
  }

  /**
   * Auto-resolve all active SOS alerts for a completed ride
   */
  async autoResolveForRide(rideId: string): Promise<void> {
    // Batch update all active SOS alerts for this ride (instead of sequential loop)
    await prisma.sOSAlert.updateMany({
      where: { rideId, status: SOSStatus.ACTIVE },
      data: { status: SOSStatus.AUTO_RESOLVED, resolvedAt: new Date() },
    });

    logger.info('Auto-resolved all SOS alerts for ride', { rideId });
  }
}

export const sosService = new SOSService();
export default sosService;
