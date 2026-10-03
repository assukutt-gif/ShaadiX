import 'dotenv/config';
import crypto from 'node:crypto';
import mongoose from 'mongoose';
import { connectDatabase } from './config/database.js';
import { env } from './config/env.js';
import { Category, Provider, Service, User } from './models/index.js';

const categories = [
  ['Wedding', 'Plan ceremonies and wedding celebrations.'],
  ['Engagement', 'Celebrate a new beginning.'],
  ['College Function', 'Find services for campus events.'],
  ['Birthday', 'Bring birthday gatherings to life.'],
  ['Reception', 'Make every reception memorable.'],
  ['Corporate Event', 'Book venues and services for teams.'],
  ['Cultural Event', 'Plan community and cultural gatherings.'],
  ['Function Hall', 'Indoor function and banquet spaces.'],
  ['Catering', 'Menus and event food service.'],
  ['Photography', 'Event photography and films.'],
  ['Decoration', 'Floral styling and event decor.'],
  ['DJ & Music', 'Sound, music and entertainment.'],
  ['Makeup', 'Event and bridal makeup services.'],
  ['Mehendi', 'Mehendi artists and packages.'],
  ['Event Planner', 'End-to-end event planning.']
];

await connectDatabase();
try {
  for (const [name, description] of categories) await Category.updateOne({ name }, { $setOnInsert: { name, description, isActive: true } }, { upsert: true });
  console.info('ShaadiX categories are ready.');
  if (process.env.SEED_DEMO_DATA === 'true') {
    const fixtures = [
      { key: 'rosewood-banquet', businessName: 'Rosewood Banquet', category: 'Function Hall', city: 'Chennai', title: 'Grand Celebration Hall', price: 85000, pricingUnit: 'flat', features: ['Air conditioning', 'Bridal suite', 'Parking'] },
      { key: 'marigold-moments', businessName: 'Marigold Moments', category: 'Decoration', city: 'Bengaluru', title: 'Floral Wedding Decor', price: 1200, pricingUnit: 'perGuest', features: ['Fresh flowers', 'Mandap styling', 'Color consultation'] },
      { key: 'frame-and-light', businessName: 'Frame & Light Studio', category: 'Photography', city: 'Hyderabad', title: 'Wedding Photography', price: 65000, pricingUnit: 'flat', features: ['Two photographers', 'Edited gallery', 'Online album'] }
    ];
    const hours = Array.from({ length: 7 }, (_value, dayOfWeek) => ({ dayOfWeek, startTime: '08:00', endTime: '22:00', isAvailable: true }));
    for (const fixture of fixtures) {
      const email = 'demo.' + fixture.key + '@shaadix.example';
      let user = await User.findOne({ email });
      if (!user) user = await User.create({ name: fixture.businessName + ' Team', email, phone: '+91000000' + String(fixtures.indexOf(fixture) + 101).padStart(4, '0'), password: crypto.randomBytes(32).toString('hex'), role: 'provider', isVerified: true });
      const provider = await Provider.findOneAndUpdate({ userId: user._id }, { $setOnInsert: { userId: user._id, businessName: fixture.businessName, category: fixture.category, description: fixture.businessName + ' provides carefully planned event services with experienced staff and clear packages.', phone: user.phone, email, address: 'Main event district', city: fixture.city, priceRange: { min: fixture.price, max: fixture.price * 2 }, rating: 4.8, totalReviews: 24, isVerified: true, isActive: true, availability: hours } }, { upsert: true, new: true });
      await Service.updateOne({ providerId: provider._id, title: fixture.title }, { $setOnInsert: { providerId: provider._id, category: fixture.category, title: fixture.title, description: fixture.businessName + ' offers a curated package with professional coordination, dependable service, and transparent pricing.', price: fixture.price, pricingUnit: fixture.pricingUnit, location: fixture.city, features: fixture.features, isActive: true } }, { upsert: true });
    }
    console.info('Demo providers and service listings are ready. Demo login accounts are intentionally not created.');
  }
  if (process.argv.includes('--admin')) {
    if (!env.SEED_ADMIN_EMAIL || !env.SEED_ADMIN_PASSWORD || !process.env.SEED_ADMIN_PHONE) {
      throw new Error('Set SEED_ADMIN_EMAIL, SEED_ADMIN_PASSWORD and SEED_ADMIN_PHONE before running seed:admin.');
    }
    if (env.SEED_ADMIN_PASSWORD.length < 12) throw new Error('SEED_ADMIN_PASSWORD must be at least 12 characters.');
    const existing = await User.findOne({ email: env.SEED_ADMIN_EMAIL.toLowerCase() });
    if (existing && existing.role !== 'admin') throw new Error('That email already belongs to a non-admin account. No role was changed.');
    if (!existing) await User.create({
      name: 'ShaadiX Admin', email: env.SEED_ADMIN_EMAIL.toLowerCase(),
      phone: process.env.SEED_ADMIN_PHONE, password: env.SEED_ADMIN_PASSWORD,
      role: 'admin', isVerified: true
    });
    console.info('Admin account is ready. Remove the seed credentials from the environment.');
  }
} finally {
  await mongoose.disconnect();
}

