export class ApiError extends Error {
  constructor(statusCode, message, details) { super(message); this.statusCode = statusCode; this.details = details; }
}
export const asyncHandler = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
export function ok(res, data, message = 'Operation successful', statusCode = 200, meta) {
  const result = { success: true, message, data }; if (meta) result.meta = meta; return res.status(statusCode).json(result);
}
export function pageOptions(query) {
  const page = Math.max(1, Number.parseInt(query.page, 10) || 1);
  const limit = Math.min(100, Math.max(1, Number.parseInt(query.limit, 10) || 20));
  return { page, limit, skip: (page - 1) * limit, sortBy: String(query.sortBy || 'createdAt'), order: query.order === 'asc' ? 1 : -1 };
}
export const pagination = (page, limit, total) => ({ page, limit, total, pages: Math.ceil(total / limit), hasNext: page * limit < total });
export const escapeRegex = (value) => String(value).split('').map((char) => '.+?^$()[]{}|*\\'.includes(char) ? '\\' + char : char).join('').slice(0, 100);

