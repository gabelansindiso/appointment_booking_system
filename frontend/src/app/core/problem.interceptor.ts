import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiProblem } from './api.models';

export function toProblem(err: unknown): ApiProblem {

  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return {
        status: 0,
        detail: "We couldn't reach the server. Please check your connection and try again."
      };
    }
    const body = typeof err.error === 'object' && err.error !== null
      ? (err.error as Partial<ApiProblem>) : null;
    return {
      status: err.status,
      detail: body?.detail ?? 'Something went wrong. Please try again.',
      errors: body?.errors,
    };
  }
  return {
    status: -1,
    detail: 'Something went wrong. Please try again.'
  };
}

export const problemInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(catchError((err: unknown) => throwError(() => toProblem(err))));
