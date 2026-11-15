import fixture from './f02-snapshot.json';
import type { ConfirmationRequest, DeliveryRequest, DeliveryResponse } from '../../src/lib/goods';
import { GoodsError, type Actor, type Command, type Snapshot } from './model';

export const reviewActors = {
  organizationAdmin: fixture.actors.organizationAdmin.userId,
  fieldVolunteer: fixture.actors.fieldVolunteer.userId,
  caseVerifier: fixture.actors.caseVerifier.userId,
  donor: fixture.actors.donor.userId,
  independentConfirmer: '70000000-0000-4000-8000-000000000005',
  platformAdmin: '70000000-0000-4000-8000-000000000006',
} satisfies Record<Actor | 'independentConfirmer' | 'platformAdmin', string>;

export type ReviewActor = keyof typeof reviewActors;
export type DeliveryReview = {
  id: string;
  recorderId: string;
  outcome: 'DELIVERED' | 'FAILED';
  status: 'PENDING' | 'CONFIRMED' | 'REQUIRES_REVIEW' | 'VOIDED' | 'NOT_APPLICABLE';
  items: { campaignItemId: string; quantity: number }[];
  evidenceVersionIds: string[];
};
type Dispute = {
  id: string;
  deliveryId?: string;
  reason: string;
  status: 'OPEN' | 'RESOLVED';
  resolution?: 'UPHOLD' | 'VOID_DELIVERY';
  resolutionReason?: string;
};

export type ReviewState = {
  deliveries: DeliveryReview[];
  disputes: Dispute[];
  events: { id: string; label: string; blockchainStatus: 'PENDING_ON_CHAIN' | 'NOT_APPLICABLE' }[];
};

export type DisputeRequest = { deliveryId?: string; reason: string };
export type ResolveDisputeRequest = {
  outcome: 'UPHOLD' | 'VOID_DELIVERY';
  reason: string;
  reviewEvidenceVersionIds: string[];
};
export type ReviewCommand =
  | { operation: 'D02'; deliveryId: string; body: ConfirmationRequest }
  | { operation: 'S01'; body: DisputeRequest }
  | { operation: 'S03'; disputeId: string; body: ResolveDisputeRequest };

export function emptyReview(): ReviewState {
  return { deliveries: [], disputes: [], events: [] };
}

export function hasOpenDispute(state: ReviewState): boolean {
  return state.disputes.some(dispute => dispute.status === 'OPEN');
}

export function canCompleteCampaign(snapshot: Snapshot, state: ReviewState): boolean {
  return snapshot.campaign.status === 'FULFILLED' && !hasOpenDispute(state)
    && state.deliveries.every(delivery => delivery.status !== 'PENDING' && delivery.status !== 'REQUIRES_REVIEW');
}

export function deliveryRequest(command: Command): DeliveryRequest {
  if (command.outcome === 'FAILED') {
    return { outcome: 'FAILED', reason: command.reason.trim(), items: [], evidenceVersionIds: [] };
  }
  return {
    outcome: 'DELIVERED',
    items: command.lines.map(({ id: campaignItemId, quantity }) => ({ campaignItemId, quantity })),
    evidenceVersionIds: [...command.evidence],
    ...(command.at ? { handedOverAt: command.at } : {}),
  };
}

export function fixtureDeliveryResponse(actor: ReviewActor, body: DeliveryRequest, id: string, auditEventId: string, recordedAt: string): DeliveryResponse {
  const record = { id, caseId: fixture.caseId, recorderId: reviewActors[actor], auditEventId, recordedAt };
  if (body.outcome === 'FAILED') return {
    ...record, outcome: 'FAILED', items: [], evidenceVersionIds: [],
    reviewStatus: 'NOT_APPLICABLE', blockchainStatus: 'NOT_APPLICABLE', auditUrl: null,
  };
  return {
    ...record, outcome: 'DELIVERED', items: body.items, evidenceVersionIds: body.evidenceVersionIds,
    reviewStatus: 'PENDING', blockchainStatus: 'PENDING_ON_CHAIN',
    auditUrl: `/api/public/cases/0x${fixture.publicCaseRef.slice(2)}/audit`,
  };
}

export function recordDelivery(state: ReviewState, delivery: DeliveryResponse): ReviewState {
  if (state.deliveries.some(record => record.id === delivery.id)) return state;
  return {
    ...state,
    deliveries: [...state.deliveries, {
      id: delivery.id, recorderId: delivery.recorderId, outcome: delivery.outcome,
      status: delivery.reviewStatus,
      items: delivery.items.map(item => ({ ...item })),
      evidenceVersionIds: [...delivery.evidenceVersionIds],
    }],
    events: [...state.events, {
      id: delivery.auditEventId,
      label: delivery.outcome === 'DELIVERED' ? 'Delivery recorded' : 'Delivery attempt failed',
      blockchainStatus: delivery.blockchainStatus,
    }],
  };
}

export function reviewPermission(operation: ReviewCommand['operation'], actor: ReviewActor, delivery?: DeliveryReview) {
  switch (operation) {
    case 'D02':
      // The recorder option models a volunteer who also holds DELIVERY_CONFIRMER.
      return (actor === 'independentConfirmer' || actor === 'fieldVolunteer') &&
        (!delivery || delivery.recorderId !== reviewActors[actor]);
    case 'S01':
      return actor === 'organizationAdmin' || actor === 'caseVerifier' ||
        actor === 'independentConfirmer' || actor === 'fieldVolunteer';
    case 'S03':
      return actor === 'platformAdmin';
  }
}

function reasonText(reason: string): string {
  if (!reason.trim() || reason.trim().length > 2000) throw new GoodsError(400, 'VALIDATION_ERROR');
  return reason.trim();
}

function changeTotals(snapshot: Snapshot, delivery: DeliveryReview, change: 'confirm' | 'void') {
  for (const line of delivery.items) {
    const item = snapshot.campaign.items.find(entry => entry.id === line.campaignItemId);
    if (!item) throw new GoodsError(404, 'RESOURCE_NOT_FOUND');
    if (change === 'confirm') {
      if (item.confirmedQty + line.quantity > item.deliveredQty) throw new GoodsError(409, 'INVALID_STATE');
      item.confirmedQty += line.quantity;
    } else {
      const confirmedReduction = delivery.status === 'CONFIRMED' ? line.quantity : 0;
      if (item.deliveredQty - line.quantity < item.confirmedQty - confirmedReduction || item.confirmedQty < confirmedReduction) {
        throw new GoodsError(409, 'INVALID_STATE');
      }
      item.deliveredQty -= line.quantity;
      item.confirmedQty -= confirmedReduction;
    }
  }
}

export function applyReview(snapshot: Snapshot, state: ReviewState, actor: ReviewActor, command: ReviewCommand, eventId: string) {
  const next = structuredClone(snapshot);
  const review = structuredClone(state);
  const emit = (label: string) => review.events.push({ id: eventId, label, blockchainStatus: 'PENDING_ON_CHAIN' });

  if (command.operation === 'D02') {
    const delivery = review.deliveries.find(entry => entry.id === command.deliveryId);
    if (!delivery) throw new GoodsError(404, 'RESOURCE_NOT_FOUND');
    if (!reviewPermission('D02', actor, delivery)) {
      throw new GoodsError(403, delivery.recorderId === reviewActors[actor] ? 'SELF_CONFIRMATION' : 'FORBIDDEN');
    }
    if (delivery.outcome !== 'DELIVERED' || delivery.status !== 'PENDING' || hasOpenDispute(review)) {
      throw new GoodsError(409, 'INVALID_STATE');
    }
    const { reviewedEvidenceVersionIds, decision, reason } = command.body;
    if (!delivery.evidenceVersionIds.length || reviewedEvidenceVersionIds.length !== delivery.evidenceVersionIds.length ||
        new Set(reviewedEvidenceVersionIds).size !== reviewedEvidenceVersionIds.length ||
        reviewedEvidenceVersionIds.some(id => !delivery.evidenceVersionIds.includes(id))) {
      throw new GoodsError(400, 'VALIDATION_ERROR');
    }
    if ((reason?.trim().length ?? 0) > 2000) throw new GoodsError(400, 'VALIDATION_ERROR');
    if (decision === 'REQUIRES_REVIEW') {
      review.disputes.push({ id: eventId, deliveryId: delivery.id, reason: reasonText(reason ?? ''), status: 'OPEN' });
      delivery.status = 'REQUIRES_REVIEW';
      emit('Delivery needs review · Dispute opened');
    } else {
      changeTotals(next, delivery, 'confirm');
      delivery.status = 'CONFIRMED';
      emit('Delivery confirmed');
    }
  } else if (command.operation === 'S01') {
    if (!reviewPermission('S01', actor)) throw new GoodsError(403, 'FORBIDDEN');
    const { deliveryId, reason } = command.body;
    if (deliveryId && !review.deliveries.some(entry =>
      entry.id === deliveryId && entry.outcome === 'DELIVERED' && entry.status !== 'VOIDED')) {
      throw new GoodsError(404, 'RESOURCE_NOT_FOUND');
    }
    review.disputes.push({ id: eventId, deliveryId, reason: reasonText(reason), status: 'OPEN' });
    emit('Dispute opened');
  } else {
    if (!reviewPermission('S03', actor)) throw new GoodsError(403, 'FORBIDDEN');
    const dispute = review.disputes.find(entry => entry.id === command.disputeId);
    if (!dispute) throw new GoodsError(404, 'RESOURCE_NOT_FOUND');
    if (dispute.status !== 'OPEN') throw new GoodsError(409, 'INVALID_STATE');
    const { outcome, reason, reviewEvidenceVersionIds } = command.body;
    const resolutionReason = reasonText(reason);
    const delivery = review.deliveries.find(entry => entry.id === dispute.deliveryId);
    if (new Set(reviewEvidenceVersionIds).size !== reviewEvidenceVersionIds.length ||
        reviewEvidenceVersionIds.some(id => !delivery?.evidenceVersionIds.includes(id))) throw new GoodsError(400, 'VALIDATION_ERROR');
    if (outcome === 'VOID_DELIVERY') {
      if (!delivery) throw new GoodsError(400, 'VALIDATION_ERROR');
      if (delivery.status === 'VOIDED') throw new GoodsError(409, 'INVALID_STATE');
      changeTotals(next, delivery, 'void');
      delivery.status = 'VOIDED';
    }
    dispute.status = 'RESOLVED';
    dispute.resolution = outcome;
    dispute.resolutionReason = resolutionReason;
    if (delivery?.status === 'REQUIRES_REVIEW' &&
        !review.disputes.some(entry => entry.status === 'OPEN' && entry.deliveryId === delivery.id)) {
      delivery.status = 'PENDING';
    }
    emit(outcome === 'VOID_DELIVERY' ? 'Delivery voided · Dispute resolved' : 'Dispute resolved · Upheld');
  }

  // An open dispute blocks writes. Fulfillment still reflects effective confirmed quantities.
  next.campaign.status = next.campaign.items.every(item => item.confirmedQty >= item.targetQty)
    ? 'FULFILLED'
    : 'PUBLISHED';
  return { snapshot: next, review };
}
