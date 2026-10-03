import { ApiError } from '../utils/http.js';
export const validate = (schema, part = 'body') => (req, _res, next) => {
  const result = schema.safeParse(req[part]);
  if (!result.success) return next(new ApiError(422, 'Please check the submitted information.', result.error.issues.map((issue) => ({ field: issue.path.join('.'), message: issue.message }))));
  if (part === 'body') req.body = result.data;
  else { req.validated = req.validated || {}; req.validated[part] = result.data; }
  next();
};

