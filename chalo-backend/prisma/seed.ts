// ============================================================
// Chalo Backend — Database Seed
// Creates initial platform config + test data for development
// ============================================================

import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  console.log('🌱 Seeding database...\n');

  // -------------------------------------------------------
  // 1. Platform Configuration (runtime-configurable values)
  // -------------------------------------------------------

  const configs = [
    { key: 'commission_percentage', value: '15', description: 'Commission % taken from each ride (commission plan drivers)' },
    { key: 'subscription_fee_weekly', value: '199', description: 'Weekly subscription fee in INR (subscription plan drivers)' },
    { key: 'surge_enabled', value: 'true', description: 'Whether surge pricing is active' },
    { key: 'surge_multiplier', value: '1.0', description: 'Current global surge multiplier (override)' },
    { key: 'min_fare', value: '30', description: 'Minimum fare in INR' },
    { key: 'base_fare_per_km', value: '12', description: 'Fare per km in INR' },
    { key: 'base_fare_per_min', value: '2', description: 'Fare per minute in INR' },
    { key: 'settlement_days', value: '2', description: 'T+N days for earnings settlement' },
  ];

  for (const cfg of configs) {
    await prisma.platformConfig.upsert({
      where: { key: cfg.key },
      update: { value: cfg.value, description: cfg.description },
      create: cfg,
    });
    console.log(`  ✓ Config: ${cfg.key} = ${cfg.value}`);
  }

  console.log('\n✅ Database seeded successfully!\n');
  console.log('Platform Config:');
  console.log('  Commission:    15%');
  console.log('  Subscription:  ₹199/week');
  console.log('  Surge:         Enabled');
  console.log('  Min Fare:      ₹30');
  console.log('  Per Km:        ₹12');
  console.log('  Per Min:       ₹2');
  console.log('  Settlement:    T+2 days');
}

main()
  .catch((e) => {
    console.error('❌ Seed failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
