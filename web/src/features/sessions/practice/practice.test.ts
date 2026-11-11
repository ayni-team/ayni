import { describe, expect, it } from 'vitest';
import { ApiError } from '@/shared/api';
import { endedFrom, openRefusal, practiceIsOver, practiceLink } from './practice';

function failure(status: number) {
  return new ApiError(status, 'x', '/api/v1/practice-rooms');
}

describe('practice rooms', () => {
  it('builds the whole link from the origin and the path the backend answered', () => {
    expect(practiceLink('http://192.168.1.20:5173', '/sesion/abc')).toBe('http://192.168.1.20:5173/sesion/abc');
    expect(practiceLink('https://app.example.org/', '/sesion/abc')).toBe('https://app.example.org/sesion/abc');
    expect(practiceLink('http://localhost:5173', undefined)).toBeNull();
    expect(practiceLink('http://localhost:5173', 'https://elsewhere.example/x')).toBeNull();
  });

  it('reads a 404 as the switch being off and a 409 as too many rooms', () => {
    expect(openRefusal(failure(404))).toBe('OFF');
    expect(openRefusal(failure(409))).toBe('FULL');
    expect(openRefusal(failure(500))).toBe('OTHER');
    expect(openRefusal(new Error('offline'))).toBe('OTHER');
  });

  it('ends a practice room when it completes or is forgotten, and never a real session', () => {
    expect(practiceIsOver({ practice: true, status: 'COMPLETED' }, null)).toBe(true);
    expect(practiceIsOver({ practice: true, status: 'IN_PROGRESS' }, failure(404))).toBe(true);
    expect(practiceIsOver({ practice: true, status: 'IN_PROGRESS' }, failure(500))).toBe(false);
    expect(practiceIsOver({ practice: true, status: 'IN_PROGRESS' }, null)).toBe(false);
    expect(practiceIsOver({ practice: false, status: 'COMPLETED' }, null)).toBe(false);
    expect(practiceIsOver({ status: 'COMPLETED' }, failure(404))).toBe(false);
    expect(practiceIsOver(undefined, failure(404))).toBe(false);
  });

  it('knows when it was sent back from a room that ended', () => {
    expect(endedFrom({ practiceEnded: true })).toBe(true);
    expect(endedFrom(null)).toBe(false);
    expect(endedFrom({ other: 1 })).toBe(false);
  });
});
