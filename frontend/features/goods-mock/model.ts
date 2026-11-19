import fixture from './f02-snapshot.json';
import { readableError } from '../../src/lib/error-message';

// F02 snapshots are private simulated read models, never production API responses.
export type Snapshot = typeof fixture.states.S1;
export type Action = 'receipt' | 'delivery';
export type Actor = 'organizationAdmin' | 'fieldVolunteer' | 'caseVerifier' | 'donor';
export type Line = { id: string; quantity: number };
export class GoodsError extends Error {
  /** What the screen shows: the specific detail, or a plain explanation of the code. */
  readonly userMessage: string;
  constructor(public status: number, public code: string, public lineId?: string, detail?: string) {
    // The status/code prefix stays in `message` for logs and tests, never in the UI.
    super(`${status} ${code}${detail ? ' — ' + detail : ''}`);
    this.userMessage = readableError(code, detail);
  }
}
export const evidenceIds = fixture.evidenceIds;
export const contributionId = fixture.states.S1.focalContribution.id;
export function initialSnapshot(partial = false): Snapshot {
  return structuredClone(partial ? fixture.states.S2 : fixture.states.S1) as Snapshot;
}
export function permitted(action: Action, actor: Actor): boolean {
  const role = action === 'receipt' ? 'ORGANIZATION_ADMIN' : 'FIELD_VOLUNTEER';
  return fixture.actors[actor].memberships.some(m => m.organizationId === fixture.organizationId && m.active && (m.roles as string[]).includes(role));
}
export function limits(snapshot: Snapshot, action: Action) {
  return snapshot.campaign.items.map(item => {
    const pledge = snapshot.focalContribution.items.find(line => line.campaignItemId === item.id)!;
    return { id: action === 'receipt' ? pledge.id : item.id, name: item.name, unit: item.unit,
      available: action === 'receipt' ? pledge.outstandingQty : item.receivedQty - item.deliveredQty };
  });
}
export type Command = { action: Action; actor: Actor; lines: Line[]; outcome: 'DELIVERED' | 'FAILED'; reason: string; evidence: string[]; at?: string; note: string };
export function apply(snapshot: Snapshot, command: Command, conflict = false): Snapshot {
  if (!permitted(command.action, command.actor)) throw new GoodsError(403, 'FORBIDDEN');
  if (command.reason.length > 2000 || command.note.length > 500 ||
      ((command.action === 'receipt' || command.at !== undefined) && !Number.isFinite(Date.parse(command.at ?? '')))) {
    throw new GoodsError(400, 'VALIDATION_ERROR');
  }
  const failed = command.action === 'delivery' && command.outcome === 'FAILED';
  if (failed) {
    if (!command.reason.trim() || command.lines.length || command.evidence.length) throw new GoodsError(400, 'VALIDATION_ERROR');
  } else {
    if (!command.lines.length || new Set(command.lines.map(line => line.id)).size !== command.lines.length) throw new GoodsError(400, 'VALIDATION_ERROR');
    const available = limits(snapshot, command.action);
    for (const line of command.lines) {
      const item = available.find(item => item.id === line.id);
      if (!item || !Number.isSafeInteger(line.quantity) || line.quantity < 1 || line.quantity > 1_000_000) throw new GoodsError(400, 'VALIDATION_ERROR', line.id, 'Enter a whole quantity from 1 to 1,000,000 for a known item.');
      if (line.quantity > item.available) throw new GoodsError(409, 'QUANTITY_EXCEEDED', line.id, `${item.name}: only ${item.available} ${item.unit} ${command.action === 'receipt' ? 'remain unreceived' : 'are available to deliver'}.`);
    }
    if (command.action === 'delivery' && (!command.evidence.length || command.evidence.some(id => !evidenceIds.includes(id)))) throw new GoodsError(400, 'EVIDENCE_REQUIRED');
  }
  if (conflict) throw new GoodsError(409, 'QUANTITY_EXCEEDED');
  const next = structuredClone(snapshot);
  if (failed) return next; // A failed attempt has no effective stock or chain effect.
  for (const line of command.lines) {
    if (command.action === 'receipt') {
      const pledge = next.focalContribution.items.find(item => item.id === line.id)!;
      pledge.receivedQty += line.quantity;
      pledge.outstandingQty -= line.quantity;
      next.campaign.items.find(item => item.id === pledge.campaignItemId)!.receivedQty += line.quantity;
    } else next.campaign.items.find(item => item.id === line.id)!.deliveredQty += line.quantity;
  }
  if (command.action === 'receipt') next.focalContribution.status = next.focalContribution.items.every(item => item.outstandingQty === 0) ? 'RECEIVED' : 'PARTIALLY_RECEIVED';
  return next;
}
