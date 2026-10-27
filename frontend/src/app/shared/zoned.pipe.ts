import { Pipe, PipeTransform } from '@angular/core';
import { formatZoned, ZonedFormat } from '../core/time';

@Pipe({ name: 'zoned' })
export class ZonedPipe implements PipeTransform {
  transform(iso: string, timeZone: string, kind: ZonedFormat = 'datetime'): string {
    return formatZoned(iso, timeZone, kind);
  }
}
