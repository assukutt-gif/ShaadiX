import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';

const { Schema, model } = mongoose;
const objectId = Schema.Types.ObjectId;

const userSchema = new Schema({
  name: { type: String, required: true, trim: true, maxlength: 100 },
  email: { type: String, required: true, unique: true, lowercase: true, trim: true, index: true },
  phone: { type: String, required: true, unique: true, trim: true, index: true },
  password: { type: String, required: true, select: false, minlength: 10 },
  profileImage: { type: String, default: '' },
  role: { type: String, enum: ['customer', 'provider', 'admin'], default: 'customer', immutable: true },
  location: { type: String, trim: true, maxlength: 160, default: '' },
  isVerified: { type: Boolean, default: false },
  isActive: { type: Boolean, default: true },
  tokenVersion: { type: Number, default: 0 }
}, { timestamps: true });
userSchema.pre('save', async function hashPassword() {
  if (this.isModified('password')) this.password = await bcrypt.hash(this.password, 12);
});
userSchema.methods.comparePassword = function comparePassword(candidate) { return bcrypt.compare(candidate, this.password); };

const availabilitySchema = new Schema({
  dayOfWeek: { type: Number, min: 0, max: 6, required: true },
  startTime: { type: String, match: /^([01][0-9]|2[0-3]):[0-5][0-9]$/, required: true },
  endTime: { type: String, match: /^([01][0-9]|2[0-3]):[0-5][0-9]$/, required: true },
  isAvailable: { type: Boolean, default: true }
}, { _id: false });
const providerSchema = new Schema({
  userId: { type: objectId, ref: 'User', required: true, unique: true, index: true },
  businessName: { type: String, required: true, trim: true, maxlength: 120 },
  category: { type: String, required: true, trim: true, maxlength: 80, index: true },
  description: { type: String, required: true, trim: true, maxlength: 3000 },
  phone: { type: String, required: true, trim: true },
  email: { type: String, required: true, lowercase: true, trim: true },
  address: { type: String, required: true, trim: true, maxlength: 250 },
  city: { type: String, required: true, trim: true, maxlength: 100, index: true },
  latitude: { type: Number, min: -90, max: 90 },
  longitude: { type: Number, min: -180, max: 180 },
  images: [{ type: String }],
  priceRange: { min: { type: Number, min: 0, default: 0 }, max: { type: Number, min: 0, default: 0 } },
  rating: { type: Number, min: 0, max: 5, default: 0 },
  totalReviews: { type: Number, min: 0, default: 0 },
  isVerified: { type: Boolean, default: false, index: true },
  isActive: { type: Boolean, default: true, index: true },
  availability: { type: [availabilitySchema], default: [] },
  blockedDates: [{ type: String }]
}, { timestamps: true });

const serviceSchema = new Schema({
  providerId: { type: objectId, ref: 'Provider', required: true, index: true },
  category: { type: String, required: true, trim: true, maxlength: 80, index: true },
  title: { type: String, required: true, trim: true, maxlength: 140 },
  description: { type: String, required: true, trim: true, maxlength: 4000 },
  images: [{ type: String }],
  price: { type: Number, required: true, min: 0 },
  pricingUnit: { type: String, enum: ['flat', 'perGuest'], default: 'flat' },
  location: { type: String, required: true, trim: true, maxlength: 180 },
  availability: [{ type: String }],
  features: [{ type: String, trim: true, maxlength: 100 }],
  isActive: { type: Boolean, default: true, index: true }
}, { timestamps: true });
serviceSchema.index({ title: 'text', description: 'text', category: 'text', location: 'text' });

const bookingSchema = new Schema({
  userId: { type: objectId, ref: 'User', required: true, index: true },
  providerId: { type: objectId, ref: 'Provider', required: true, index: true },
  serviceId: { type: objectId, ref: 'Service', required: true },
  eventType: { type: String, required: true, trim: true, maxlength: 80 },
  eventDate: { type: String, required: true, match: /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/ },
  startTime: { type: String, required: true, match: /^([01][0-9]|2[0-3]):[0-5][0-9]$/ },
  endTime: { type: String, required: true, match: /^([01][0-9]|2[0-3]):[0-5][0-9]$/ },
  guestCount: { type: Number, required: true, min: 1, max: 100000 },
  eventLocation: { type: String, required: true, trim: true, maxlength: 300 },
  specialRequirements: { type: String, trim: true, maxlength: 2000, default: '' },
  totalAmount: { type: Number, required: true, min: 0 },
  paymentStatus: { type: String, enum: ['PENDING', 'PAID', 'FAILED', 'REFUNDED'], default: 'PENDING', index: true },
  bookingStatus: { type: String, enum: ['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPLETED'], default: 'PENDING', index: true },
  cancellationReason: { type: String, trim: true, maxlength: 500, default: '' }
}, { timestamps: true });
bookingSchema.index({ providerId: 1, eventDate: 1, startTime: 1, bookingStatus: 1 });
bookingSchema.index({ userId: 1, createdAt: -1 });

const bookingSlotSchema = new Schema({
  providerId: { type: objectId, ref: 'Provider', required: true },
  eventDate: { type: String, required: true },
  slot: { type: String, required: true },
  bookingId: { type: objectId, ref: 'Booking', required: true }
}, { timestamps: true });
bookingSlotSchema.index({ providerId: 1, eventDate: 1, slot: 1 }, { unique: true });
bookingSlotSchema.index({ bookingId: 1 });

const reviewSchema = new Schema({
  userId: { type: objectId, ref: 'User', required: true, index: true },
  providerId: { type: objectId, ref: 'Provider', required: true, index: true },
  bookingId: { type: objectId, ref: 'Booking', required: true, unique: true },
  rating: { type: Number, required: true, min: 1, max: 5 },
  comment: { type: String, required: true, trim: true, maxlength: 2000 },
  images: [{ type: String }],
  isVisible: { type: Boolean, default: true },
  isReported: { type: Boolean, default: false, index: true },
  reportReason: { type: String, trim: true, maxlength: 500, default: '' }
}, { timestamps: true });

const favoriteSchema = new Schema({
  userId: { type: objectId, ref: 'User', required: true },
  providerId: { type: objectId, ref: 'Provider', required: true }
}, { timestamps: { createdAt: true, updatedAt: false } });
favoriteSchema.index({ userId: 1, providerId: 1 }, { unique: true });

const notificationSchema = new Schema({
  userId: { type: objectId, ref: 'User', required: true, index: true },
  title: { type: String, required: true, trim: true, maxlength: 120 },
  message: { type: String, required: true, trim: true, maxlength: 500 },
  type: { type: String, enum: ['BOOKING', 'PAYMENT', 'REVIEW', 'OFFER', 'SYSTEM'], default: 'SYSTEM' },
  data: { type: Schema.Types.Mixed, default: {} },
  isRead: { type: Boolean, default: false, index: true }
}, { timestamps: true });
notificationSchema.index({ userId: 1, createdAt: -1 });

const paymentSchema = new Schema({
  userId: { type: objectId, ref: 'User', required: true, index: true },
  bookingId: { type: objectId, ref: 'Booking', required: true, index: true },
  amount: { type: Number, required: true, min: 0 },
  paymentMethod: { type: String, enum: ['UPI', 'CARD', 'NET_BANKING', 'WALLET', 'PAY_LATER', 'RAZORPAY'], default: 'RAZORPAY' },
  transactionId: { type: String, trim: true, default: '' },
  razorpayOrderId: { type: String, trim: true, default: '', index: true },
  status: { type: String, enum: ['PENDING', 'PAID', 'FAILED', 'REFUNDED'], default: 'PENDING', index: true }
}, { timestamps: true });
paymentSchema.index({ transactionId: 1 }, { unique: true, sparse: true });

const categorySchema = new Schema({
  name: { type: String, required: true, unique: true, trim: true, maxlength: 100 },
  description: { type: String, trim: true, maxlength: 500, default: '' },
  image: { type: String, default: '' },
  isActive: { type: Boolean, default: true }
}, { timestamps: true });

const otpSchema = new Schema({
  userId: { type: objectId, ref: 'User', index: true },
  email: { type: String, required: true, lowercase: true, index: true },
  purpose: { type: String, enum: ['verify', 'reset'], required: true },
  codeHash: { type: String, required: true },
  attempts: { type: Number, default: 0 },
  expiresAt: { type: Date, required: true, expires: 0 }
}, { timestamps: true });
otpSchema.index({ email: 1, purpose: 1 });

const refreshSessionSchema = new Schema({
  userId: { type: objectId, ref: 'User', required: true, index: true },
  tokenHash: { type: String, required: true, unique: true },
  expiresAt: { type: Date, required: true, expires: 0 },
  revokedAt: { type: Date, default: null }
}, { timestamps: true });

export const User = model('User', userSchema);
export const Provider = model('Provider', providerSchema);
export const Service = model('Service', serviceSchema);
export const Booking = model('Booking', bookingSchema);
export const BookingSlot = model('BookingSlot', bookingSlotSchema);
export const Review = model('Review', reviewSchema);
export const Favorite = model('Favorite', favoriteSchema);
export const Notification = model('Notification', notificationSchema);
export const Payment = model('Payment', paymentSchema);
export const Category = model('Category', categorySchema);
export const OtpChallenge = model('OtpChallenge', otpSchema);
export const RefreshSession = model('RefreshSession', refreshSessionSchema);

