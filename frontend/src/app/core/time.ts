const LOCALE = 'en-ZA';

export function todayIn(timeZone: string, now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(now);
}

export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

export type ZonedFormat = 'time' | 'date' | 'datetime';

export function formatZoned(iso: string, timeZone: string, kind: ZonedFormat = 'datetime'): string {
  const instant = new Date(iso);
  const time = new Intl.DateTimeFormat(LOCALE, {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23' }).format(instant);
  const date = new Intl.DateTimeFormat(LOCALE, {
    timeZone,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric' }).format(instant);
  return kind === 'time' ? time : kind === 'date' ? date : `${date} at ${time}`;
}

export function maskEmail(email: string): string {
  const at = email.indexOf('@');
  return at < 1 ? '***' : `${email[0]}***${email.slice(at)}`;
}
