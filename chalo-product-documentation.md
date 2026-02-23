# Chalo (ਚਲੋ) — Product Documentation
> **Faridabad's Hyper-Local Bike Ride-Hailing App**
> *"Faridabad da apna safar"*

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Personas](#2-personas)
3. [Conversation Journey & Discovery Log](#3-conversation-journey--discovery-log)
4. [Assumptions — Original vs Ground Truth](#4-assumptions--original-vs-ground-truth)
5. [Product Requirements (Locked)](#5-product-requirements-locked)
6. [Business Model](#6-business-model)
7. [Technical Constraints](#7-technical-constraints)
8. [Complete Screen Inventory](#8-complete-screen-inventory)
9. [User Flows](#9-user-flows)
10. [Design System](#10-design-system)
11. [Safety & Trust Features](#11-safety--trust-features)
12. [Expansion Roadmap](#12-expansion-roadmap)
13. [Open Decisions & Next Steps](#13-open-decisions--next-steps)

---

## 1. Project Overview

| Field | Detail |
|---|---|
| **App Name** | Chalo (ਚਲੋ) — meaning "Let's Go" in Punjabi |
| **Tagline** | "Faridabad da apna safar" |
| **Type** | Native Android App (iOS — Phase 2) |
| **Market** | Faridabad, Haryana |
| **Problem** | Rapido and Uber do not operate in Faridabad. Local commuters have no reliable, digitally-bookable ride option. Local riders have no platform to find customers. |
| **Solution** | A hyper-local, community-owned ride-hailing platform built specifically for Faridabad's riders and customers, starting with bike rides. |
| **V1 Vehicle Type** | Bike (pillion passenger rides only) |
| **V2 Vehicle Type** | Auto-rickshaw |
| **V3+ Services** | Parcel delivery, food delivery, cab |
| **Primary Language** | Punjabi (Gurmukhi script) + English |
| **Design Philosophy** | Mobile-first, low-end Android optimized, zero unnecessary complexity |

---

## 2. Personas

### Persona 1 — The Bike Rider (Driver)

| Attribute | Detail |
|---|---|
| **Who they are** | Local Faridabad youth or working-age men who own a bike and want to earn extra income |
| **Age Range** | 18–40 years |
| **Device** | Android 9–13, mix of budget and mid-range phones |
| **Data Plan** | Active mobile data plan (confirmed) |
| **Tech Comfort** | Comfortable with smartphones — uses WhatsApp, YouTube, UPI daily |
| **Map Literacy** | Can use maps but prefers regional language (Punjabi) labels |
| **Current Reality** | No platform serves them. They wait at stands or rely on word-of-mouth |
| **Income Anxiety** | High — they currently keep 100% of fare. Platform commission is a concern |
| **Document Readiness** | Has driving license, RC, Aadhar. Willing to upload digitally |
| **Pain Points** | Inconsistent earnings, no demand forecasting, no digital payments |
| **Motivation to Join Chalo** | Steady stream of ride requests, digital earnings tracking, flexibility |
| **Critical Need in App** | Fast ride request screen, clear earnings display, simple navigation |

---

### Persona 2 — The Customer (Rider)

#### Sub-Persona A: Older / General Demographic

| Attribute | Detail |
|---|---|
| **Who they are** | Working adults, homemakers, daily commuters in Faridabad |
| **Age Range** | 28–55 years |
| **Device** | Android 9–12, budget to mid-range |
| **Payment Preference** | Cash primary |
| **Tech Comfort** | Moderate — comfortable with calling, WhatsApp, basic apps |
| **Onboarding** | May self-onboard OR approach a rider in person |
| **Trust Level** | Will need social proof and word-of-mouth before trusting a new app |
| **Key Feature Need** | Simple booking, upfront fare, ability to call driver, SOS for safety |

#### Sub-Persona B: Gen Z Customer

| Attribute | Detail |
|---|---|
| **Who they are** | Students, young professionals in Faridabad |
| **Age Range** | 16–27 years |
| **Device** | Android 10–14, mid to high-range phones |
| **Payment Preference** | Online (UPI) only |
| **Tech Comfort** | High — early adopters, will share the app socially |
| **Onboarding** | 100% digital self-onboarding |
| **Key Feature Need** | Live tracking, seamless UPI payment, ride history, rating system |

---

### Persona 3 — The Platform Operator (Admin — Future Scope)

The team managing driver onboarding, verification, earnings settlement, dispute resolution, and city expansion. Admin panel is out of V1 scope but should be architected for from day one.

---

## 3. Conversation Journey & Discovery Log

This section documents the sequence of decisions made across the product discovery conversation, including what was proposed, questioned, and confirmed.

---

### Stage 1 — Initial Brief

**What was requested:** A UI/UX design strategy for a ride-hailing web app for Faridabad, modeled on Rapido/Uber, with driver and customer interfaces.

**Initial design produced:**
- App named "Chalo"
- Web app platform
- Hindi + English bilingual
- Auto-rickshaw + Cab vehicle types
- Commission-only model
- Google Maps-centric UI
- 30-second ride accept window
- Customer-only booking flow

---

### Stage 2 — Assumption Interrogation

Before proceeding, all major assumptions were systematically questioned:

| Assumption Challenged | Why It Mattered |
|---|---|
| Web app is right medium | Push notifications, GPS, camera — all unreliable on web |
| Hindi is the right language | Faridabad has a large Punjabi-speaking population |
| Drivers have smartphones | If not, entire app model collapses |
| Drivers will trust digital fares | They currently negotiate freely — why submit to an algorithm? |
| Typed destination addresses work | Indian tier-2 cities use landmark-based navigation |
| Real-time GPS is V1-ready | WebSockets + background GPS = significant infrastructure |
| Rating system will be useful | Small driver pools make star ratings statistically meaningless |
| Two full apps needed from Day 1 | A WhatsApp-based MVP might validate market faster |
| Commission model works | Drivers currently keep 100% — platform cut may repel them |

---

### Stage 3 — Ground Truth Confirmed by Founder

The following were confirmed through founder input, replacing original assumptions:

| Topic | Confirmed Ground Truth |
|---|---|
| Platform | Native Android app (not web) |
| Language | Punjabi + English (not Hindi) |
| Driver smartphone comfort | Yes, Android 9–13, comfortable |
| Data plans | Yes, active data plans confirmed |
| Map language | Regional Punjabi preferred |
| Document upload | Yes, digital upload confirmed |
| Accept/Reject window | Exactly 60 seconds |
| Cash payments | Primary for older users |
| Online payments | UPI primary for Gen Z |
| Business model | Commission + Subscription hybrid |
| Cancellation policy | V1: track history only. V2: charge customers |
| SOS button | Yes — women and child safety |
| Vehicle V1 | Bike riders only |
| Scheduled rides | Yes — up to 7 days in advance |
| Driver earnings settlement | T+2 days for commission model |
| Withdrawal methods | Bank transfer (UPI/NEFT) or Cash via field agent |
| Manual ride creation | Yes — driver can create a ride when customer approaches physically |
| Destination format | Typed format confirmed |
| City coverage | Entire Faridabad city, V1 |

---

## 4. Assumptions — Original vs Ground Truth

| # | Original Assumption | Ground Truth | Impact on Design |
|---|---|---|---|
| 1 | Web app | Native Android app | Push notifications, GPS, camera now reliable. Entire tech stack changes. |
| 2 | Hindi + English | Punjabi + English | All copy, labels, notifications must be rewritten in Gurmukhi script |
| 3 | Auto + Cab | Bike only (V1) | Single vehicle type simplifies ride request card and home screen |
| 4 | 30-second accept window | 60 seconds exactly | Countdown timer ring redesigned to 60s. Gives drivers time to read safely |
| 5 | Customer-only booking | Driver can also create ride manually | New screen added: Manual Ride Creation in Driver app |
| 6 | Cash only | Cash + UPI | Payment selector added to fare confirmation screen |
| 7 | Commission only | Commission + Subscription hybrid | Earnings dashboard needs to show model-specific view |
| 8 | No scheduled rides | Scheduled rides up to 7 days | New screen: Schedule Ride. New queue in Driver app |
| 9 | No SOS | SOS button required | SOS added to Active Ride screen with emergency contact setup |
| 10 | Instant earnings | T+2 settlement | Earnings dashboard shows pending vs settled amounts |
| 11 | No cancellation logic | V1: track, V2: charge | Silent cancellation history logging from Day 1 |
| 12 | One city zone handling | Entire Faridabad, no zones | No zone selector needed in V1, but city expansion architecture needed |

---

## 5. Product Requirements (Locked)

### 5.1 Functional Requirements — Customer App

| ID | Requirement | Priority |
|---|---|---|
| C-01 | Phone number login with OTP | Must Have |
| C-02 | Home screen with map and booking entry point | Must Have |
| C-03 | Destination search via typed input | Must Have |
| C-04 | Fare estimate shown before booking confirmation | Must Have |
| C-05 | Payment method selection — Cash or UPI | Must Have |
| C-06 | On-demand instant booking | Must Have |
| C-07 | Scheduled ride booking (up to 7 days ahead) | Must Have |
| C-08 | Searching for rider — live loading state | Must Have |
| C-09 | Rider found card — name, vehicle, ETA, photo | Must Have |
| C-10 | Live ride tracking screen | Must Have |
| C-11 | SOS button on active ride screen | Must Have |
| C-12 | Emergency contact setup in profile | Must Have |
| C-13 | Ride complete screen with fare summary | Must Have |
| C-14 | 1–5 star rating (optional, skippable) | Must Have |
| C-15 | Ride history with past trips | Must Have |
| C-16 | Scheduled rides manager | Must Have |
| C-17 | Cancellation history (silent in V1) | Must Have |
| C-18 | Punjabi + English language toggle | Must Have |
| C-19 | Share live ride link (WhatsApp) | Should Have |
| C-20 | Saved locations (Home, Work) | Should Have |

---

### 5.2 Functional Requirements — Driver App

| ID | Requirement | Priority |
|---|---|---|
| D-01 | Phone number login with OTP | Must Have |
| D-02 | Document upload wizard (license, RC, Aadhar, bike photo) | Must Have |
| D-03 | Verification pending screen | Must Have |
| D-04 | Dashboard with Online/Offline toggle | Must Have |
| D-05 | Incoming ride request card with 60-second countdown | Must Have |
| D-06 | Ride request shows: customer name, pickup, drop, distance, duration, fare, customer cancellation history count | Must Have |
| D-07 | Accept or Reject ride | Must Have |
| D-08 | Customer details screen after accept | Must Have |
| D-09 | Navigate to pickup (triggers Google Maps) | Must Have |
| D-10 | "I've Arrived" button (GPS-triggered within ~200m) | Must Have |
| D-11 | Active ride screen with destination + customer contact | Must Have |
| D-12 | "Complete Ride" button | Must Have |
| D-13 | Manual Ride Creation — driver enters destination when customer approaches physically | Must Have |
| D-14 | Fare confirmation screen + payment method confirmation | Must Have |
| D-15 | Earnings dashboard — today, weekly, per-trip | Must Have |
| D-16 | T+2 settlement tracker (pending vs settled) | Must Have |
| D-17 | Withdrawal screen — Bank Transfer or Cash Request | Must Have |
| D-18 | Scheduled rides queue (upcoming booked rides) | Must Have |
| D-19 | Plan manager — Commission vs Subscription toggle | Must Have |
| D-20 | Punjabi + English language toggle | Must Have |
| D-21 | Daily earnings chip visible on dashboard | Must Have |

---

### 5.3 Non-Functional Requirements

| Requirement | Detail |
|---|---|
| **Platform** | Android (API 28 / Android 9 minimum) |
| **Performance** | App must load in under 3 seconds on a 4G connection |
| **Offline resilience** | Cache last known map state; show "weak connection" indicator gracefully |
| **APK size** | Under 25MB to support budget Android devices with limited storage |
| **Background GPS** | Required for driver app — must request permission with clear explanation |
| **Push notifications** | Required for ride requests, ride status updates, scheduled ride reminders |
| **Map rendering** | Google Maps SDK with Punjabi locale support |
| **Accessibility** | Minimum 54px tap targets throughout; icon + label navigation |
| **Security** | Phone OTP auth; document images stored encrypted; live location shared only during active ride |

---

## 6. Business Model

### 6.1 Driver Plan Types

| Plan | How It Works | UI Implication |
|---|---|---|
| **Commission-Based** | Platform takes a % cut per completed ride. Driver receives net earnings after T+2 days. | Earnings dashboard shows: gross fare, commission deducted, net settled amount, pending amount |
| **Subscription-Based** | Driver pays a flat weekly fee to the platform. Keeps 100% of every fare. | Earnings dashboard shows: full fare collected, subscription fee due date |

### 6.2 Driver Earnings Withdrawal

| Method | Detail |
|---|---|
| Bank Transfer | UPI or NEFT to registered bank account |
| Cash | Field agent collects or delivers cash. Manual request via app. |
| Settlement Window | T+2 days for commission-based riders |

### 6.3 Payment Flow — Customer Side

| Demographic | Method | How Settled |
|---|---|---|
| Older users | Cash | Paid directly to driver at end of ride |
| Gen Z users | UPI | Paid via app before or after ride completion |

### 6.4 Cancellation Policy

| Phase | Policy |
|---|---|
| V1 (Launch) | Track all customer cancellations silently in the background. No penalty applied. |
| V2 | Analyze V1 data. Apply cancellation fee to customers with high cancellation history. |
| Driver Visibility | Driver sees customer's cancellation count on the incoming ride request card |

---

## 7. Technical Constraints

| Constraint | Detail |
|---|---|
| **Target devices** | Android 9–13 (budget to mid-range phones) |
| **No iOS V1** | iOS is Phase 2 |
| **Map SDK** | Google Maps Android SDK with regional language support |
| **Auth** | Firebase Phone Auth or equivalent OTP service |
| **Real-time** | WebSockets or Firebase Realtime Database for live location and ride state |
| **Background location** | Required for driver app. Needs Android foreground service |
| **Document storage** | Encrypted cloud storage (Firebase Storage or AWS S3) |
| **Payment gateway** | UPI integration via Razorpay or PayU |
| **Push notifications** | Firebase Cloud Messaging (FCM) |
| **Minimum APK size** | Under 25MB |

---

## 8. Complete Screen Inventory

### Customer App — 14 Screens

| # | Screen Name | Key Elements |
|---|---|---|
| 1 | Splash | Logo, tagline, brand animation |
| 2 | Onboarding (3 slides) | Benefit per slide, Punjabi/English, Skip button |
| 3 | Phone Login + OTP | +91 pre-filled, 4-digit OTP boxes, auto-submit |
| 4 | Home Screen | Map (55%), bottom sheet with booking bar, vehicle selector, recent places |
| 5 | Enter Destination | From (auto-filled GPS), To (typed search), landmark suggestions |
| 6 | Schedule Ride | Date + time picker, up to 7 days, confirmation summary |
| 7 | Fare Estimate + Payment | Route on map, fare range, Cash/UPI selector, Confirm button |
| 8 | Searching for Rider | Animated ripple, estimated wait time, Cancel Search option |
| 9 | Rider Found | Rider photo, name, rating, vehicle number, ETA, Call/Message buttons |
| 10 | Active Ride + SOS | Live map, rider marker, ETA, driver contact, SOS button, Share Ride |
| 11 | Ride Complete + Rating | Fare summary, 5-star rating, optional comment, Skip option |
| 12 | Ride History | Reverse chronological list, per-trip detail, filter by date |
| 13 | Scheduled Rides Manager | Upcoming scheduled rides, cancel or modify |
| 14 | Profile + Emergency Contact | Name, phone, saved places, emergency contact setup for SOS |

---

### Driver App — 14 Screens

| # | Screen Name | Key Elements |
|---|---|---|
| 1 | Splash | Logo, driver-specific tagline |
| 2 | Phone Login + OTP | Same as customer auth |
| 3 | Document Upload Wizard | 4-step: License → RC → Aadhar → Bike Photo |
| 4 | Verification Pending | Status indicator, "24 hrs" message, friendly illustration |
| 5 | Dashboard (Online) | Map, Online/Offline toggle, Today's Earnings chip, Trips chip, bottom nav |
| 6 | Incoming Ride Request | 60s countdown ring, customer name, pickup area, drop, fare, customer cancellation count, Accept/Reject |
| 7 | Customer Details + Navigate | Full customer info, pickup on map, Call Customer, Navigate to Pickup, I've Arrived |
| 8 | Active Ride Screen | Destination, ETA, customer contact, Complete Ride button |
| 9 | Manual Ride Creation | Driver enters customer destination, generates trip manually |
| 10 | Ride Complete + Fare | Fare amount, payment method confirmation (cash collected / UPI received) |
| 11 | Earnings Dashboard | Today, weekly, per-trip breakdown. Commission vs Subscription view. T+2 pending vs settled |
| 12 | Withdrawal Screen | Bank Transfer (UPI/NEFT) or Cash Request, settlement history |
| 13 | Scheduled Rides Queue | Upcoming confirmed rides for the week, time-sorted |
| 14 | Profile + Plan Manager | Driver details, Commission vs Subscription plan selector, document status |

---

## 9. User Flows

### 9.1 Customer — On-Demand Ride Flow

```
App Open
    ↓
Splash (2s) → Auth Check
    ↓                    ↓
New User            Returning User
    ↓                    ↓
Onboarding → Auth    Home Screen (Map)
    ↓
Phone Login + OTP
    ↓
Home Screen
    ↓
Tap booking bar → Enter Destination
    ↓
[Optional] Toggle: Book Now vs Schedule
    ↓
[If Schedule] → Pick date/time → Confirm schedule
    ↓
[If Now] → Fare Estimate shown → Select Cash or UPI
    ↓
Confirm Booking
    ↓
Searching for Rider (animated state)
    ↓
    ↓ No riders available → Notify + retry option
    ↓ Rider found
Rider Details Card slides up
    ↓
Active Ride Screen (live tracking)
    ↓
[Optional] SOS → Alert sent to emergency contact with live location
    ↓
Arrive at destination
    ↓
Ride Complete → Fare Summary
    ↓
[Cash] → Confirm cash paid
[UPI] → Payment processed in app
    ↓
Rate Your Ride (optional, skippable)
    ↓
Home Screen
```

---

### 9.2 Driver — Standard Ride Accept Flow

```
App Open
    ↓
Auth Check → New Driver: Document Upload → Verification Pending
    ↓
Verified Driver: Dashboard
    ↓
Driver toggles "Go Online"
    ↓
[Waiting state] Map visible, earnings chip shown
    ↓
Incoming Ride Request (push notification + in-app card)
    ↓
60-second countdown ring starts
    ↓
Driver reads: pickup area, drop, fare, customer cancellation count
    ↓
    ↓ Accept                           ↓ Reject / Timer expires
    ↓                                  → Back to waiting state
Customer Details revealed
    ↓
Navigate to Pickup (Google Maps opens)
    ↓
Within ~200m of pickup → "I've Arrived" button activates
    ↓
Driver taps I've Arrived → Customer notified
    ↓
Customer boards → Driver taps "Start Ride"
    ↓
Active Ride Screen (destination shown)
    ↓
Arrive at destination → Driver taps "Complete Ride"
    ↓
Fare Confirmation screen
    ↓
[Cash] → Confirm cash collected
[UPI] → App confirms payment received
    ↓
Per-Trip Earnings card shown
    ↓
Back to Dashboard (Online state resumes)
```

---

### 9.3 Driver — Manual Ride Creation Flow
*(When customer physically approaches the rider)*

```
Driver on Dashboard (Online or Offline)
    ↓
Taps "Create Ride Manually"
    ↓
Enters customer's destination (typed or landmark)
    ↓
Fare calculated and displayed
    ↓
[Optional] Enter customer phone number (for record)
    ↓
Select payment method: Cash or UPI
    ↓
Confirm → Ride starts
    ↓
Active Ride Screen
    ↓
Complete Ride → Earnings updated
```

---

### 9.4 SOS Trigger Flow

```
Customer on Active Ride Screen
    ↓
Taps SOS button (requires 2-second hold to prevent accidental activation)
    ↓
Confirmation dialog: "Send SOS alert?" → Confirm
    ↓
Alert sent to saved emergency contact(s) with:
  - Customer name
  - Live GPS location link
  - Rider name + vehicle number
  - Timestamp
    ↓
In-app: SOS active banner shown on ride screen
    ↓
SOS auto-deactivates when ride completes OR customer manually dismisses
```

---

### 9.5 Scheduled Ride Flow

```
Customer enters destination → Fare Estimate screen
    ↓
Toggles "Schedule for Later"
    ↓
Date + Time picker (7-day window, hourly slots)
    ↓
Confirms booking → Ride saved to Scheduled Rides
    ↓
Push notification reminder sent 30 mins before scheduled time
    ↓
At scheduled time: App auto-searches for available rider
    ↓
Standard ride flow continues from "Searching for Rider"
```

---

## 10. Design System

### 10.1 Color Palette

| Role | Color Name | Hex | Usage |
|---|---|---|---|
| Primary | Saffron Orange | `#FF6B00` | CTA buttons, active states, key highlights |
| Secondary | Deep Navy | `#1A1F3C` | Headers, navigation bar, key text |
| Success / Go | Lime Green | `#2ECC71` | Online state, confirmed, accepted |
| Danger / Stop | Coral Red | `#E74C3C` | Cancel, reject, SOS active state |
| Background | Off-White | `#F8F9FA` | Screen backgrounds |
| Surface | Pure White | `#FFFFFF` | Cards, bottom sheets, modals |
| Text Primary | Charcoal | `#2D2D2D` | All primary text |
| Text Secondary | Medium Gray | `#7F8C8D` | Labels, subtitles, placeholders |
| Countdown Ring | Amber | `#F39C12` | 60-second timer when < 20s remaining |
| Countdown Urgent | Red | `#E74C3C` | Timer ring when < 10s remaining |

---

### 10.2 Typography

| Role | Font | Size | Weight |
|---|---|---|---|
| Screen Titles | Poppins | 24px | Bold |
| Card Titles | Poppins | 18px | SemiBold |
| Body | Poppins | 14px | Regular |
| Labels / Captions | Poppins | 12px | Medium |
| CTA Buttons | Poppins | 16px | Bold |
| Punjabi Script | Noto Sans Gurmukhi | 14–18px | Regular/Medium |

---

### 10.3 Component Specs

| Component | Spec |
|---|---|
| Primary Button | Full-width, 54px height, 12px border radius, saffron fill, white bold text |
| Secondary Button | Full-width, 48px height, outlined, white fill, saffron or red border |
| Cards | 16px border radius, `box-shadow: 0 4px 12px rgba(0,0,0,0.08)`, 16px padding |
| Bottom Sheets | Slide-up animation, drag handle at top, 24px top border radius |
| Input Fields | 52px height, 8px border radius, light gray border, saffron focus state |
| Icons | Material Icons Outlined style, 24px standard, 32px for tab navigation |
| Tap Targets | Minimum 54px × 54px (WCAG AA for mobile) |
| Map Overlay | Desaturated base map, saffron rider marker, blue customer dot |

---

### 10.4 Navigation Structure

**Customer App** — Bottom Tab Bar (4 tabs):
- 🏠 Home (Map + Book)
- 📋 Rides (History + Scheduled)
- 🔔 Notifications
- 👤 Profile

**Driver App** — Bottom Tab Bar (4 tabs):
- 🏠 Dashboard (Map + Online toggle)
- 💰 Earnings
- 📅 Schedule (Upcoming rides)
- 👤 Profile

---

## 11. Safety & Trust Features

### 11.1 SOS Feature

| Element | Detail |
|---|---|
| **Location** | Active Ride screen — visible but not easily triggered accidentally |
| **Activation** | 2-second press-and-hold to prevent accidental triggers |
| **Alert content** | Customer name, live GPS link, rider name, vehicle number, timestamp |
| **Recipients** | Pre-saved emergency contacts (set up in Profile) |
| **Channel** | SMS + WhatsApp message to emergency contacts |
| **In-app state** | Red SOS banner shown for duration of alert |
| **Deactivation** | Auto on ride complete OR manual customer dismiss |
| **Future** | Integration with 112 (police helpline) — V2 consideration |

### 11.2 Trust Signals

| Feature | Purpose |
|---|---|
| Fare shown before booking | Prevents overcharging disputes |
| Rider name + vehicle number shown to customer | Accountability |
| Customer cancellation count shown to driver | Driver can make informed decisions |
| Driver rating shown to customer | Peer accountability |
| Document verification before driver goes live | Platform credibility |
| Share live ride link | Passive safety for all riders |

---

## 12. Expansion Roadmap

### Vehicle Expansion

| Phase | Vehicle | Status |
|---|---|---|
| V1 | Bike (pillion) | Launch |
| V2 | Auto-rickshaw | After product-market fit confirmed |
| V3 | Cab | Scale phase |

### Service Expansion

| Phase | Service | Status |
|---|---|---|
| V1 | Passenger rides | Launch |
| V2 | Parcel delivery | If customer love is confirmed post-V1 |
| V3 | Food delivery | Scale phase |

### Geographic Expansion

| Phase | Area | Trigger |
|---|---|---|
| V1 | All of Faridabad | Launch |
| V2 | Adjacent areas | Demand data + user requests |
| V3 | Other cities in Haryana | Population and revenue metrics |

### Design Architecture for Expansion

The home screen vehicle selector must be built as a component from Day 1, even with only "Bike" active. When autos launch, the component enables without a redesign. A city selector in Settings is visible but locked to Faridabad in V1 — unlocks on expansion. This makes growth feel like a feature reveal, not a new product.

---

## 13. Open Decisions & Next Steps

### Still To Be Decided

| # | Decision | Who Decides | When Needed |
|---|---|---|---|
| 1 | Exact commission percentage for commission model | Founder | Before driver onboarding |
| 2 | Exact weekly subscription fee | Founder | Before driver onboarding |
| 3 | Surge pricing — yes or no in V1? | Founder | Before fare estimation logic is built |
| 4 | UPI payment gateway — Razorpay vs PayU vs others | Tech team | Before payment screen is built |
| 5 | Cash field agent network for driver withdrawals — how does it work operationally? | Ops team | Before earnings withdrawal screen is finalized |
| 6 | Minimum number of drivers before public launch | Founder | Go-to-market planning |
| 7 | V1 cancellation fee threshold — how many cancellations before penalty in V2? | Founder | After 30 days of V1 data |
| 8 | Scheduled ride reminder timing — 30 mins? 1 hour? | UX decision | Before notification system is built |

---

### Recommended Next Steps (In Order)

| Step | Deliverable | Owner |
|---|---|---|
| **Step 1** | Complete user flow diagrams for both apps — all states including edge cases (no driver available, SOS trigger, payment failure, etc.) | UX Designer |
| **Step 2** | Screen-by-screen wireframe descriptions with exact layout, spacing, tap target logic | UX Designer |
| **Step 3** | Design System definition — color tokens, typography scale, component library in Figma | UI Designer |
| **Step 4** | High-fidelity Figma screens for both apps (Customer: 14 screens, Driver: 14 screens) | UI Designer |
| **Step 5** | Prototype for user testing — recruit 10 local riders + 10 local customers in Faridabad | UX Researcher / Founder |
| **Step 6** | Iterate on prototype based on real user feedback | UI/UX Designer |
| **Step 7** | Handoff documentation for developers (component specs, API contracts, state logic) | UX Designer + Tech Lead |
| **Step 8** | Android development begins — Customer app first, Driver app parallel | Dev Team |
| **Step 9** | Driver onboarding campaign in Faridabad — physical + digital | Marketing / Ops |
| **Step 10** | Soft launch with limited driver pool → gather data → iterate | Founder + Team |

---

*Document Version: 1.0*
*Last Updated: February 2026*
*Status: Design-Ready — Awaiting Wireframe Phase*
