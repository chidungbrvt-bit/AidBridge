"use client";

import { useState } from "react";
import { useSession } from "@/components/session-provider";
import { describeError, ErrorNotice, type ErrorInfo } from "@/components/cases/shared";
import type { GoodsReviewAction } from "@/lib/goods";
import type { GoodsReviewAttempt } from "@/lib/session-work";

export function useGoodsReviewWrite(
  route: string, onSaved: () => void, onConflict?: () => void, organizationId?: string,
) {
  const { api, user, goodsReview, getGoodsReview, setGoodsReview, refreshUser } = useSession();
  const pending = goodsReview?.route === route ? goodsReview : null;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [saved, setSaved] = useState(false);

  async function send(attempt: GoodsReviewAttempt) {
    const previous = getGoodsReview();
    if (attempt.inFlight || (previous && previous !== attempt)) return;
    attempt.inFlight = true;
    setGoodsReview(attempt);
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      if (previous) {
        const current = await api.me();
        if (getGoodsReview() !== attempt) return;
        if (current.userId !== attempt.actorId) {
          setGoodsReview(null);
          await refreshUser();
          return;
        }
      }
      const command = attempt.body;
      switch (command.action) {
        case "confirm": await api.confirmDelivery(command.id, command.body, attempt.key); break;
        case "dispute": await api.openCaseDispute(command.id, command.body, attempt.key); break;
        case "resolve": await api.resolveDispute(command.id, command.body, attempt.key); break;
        case "close": await api.closeCampaign(command.id, attempt.key); break;
      }
      if (getGoodsReview() !== attempt) return;
      setGoodsReview(null);
      setSaved(true);
      onSaved();
    } catch (cause) {
      if (getGoodsReview() !== attempt) return;
      const failure = describeError(cause);
      setError(failure);
      if (!failure.unknownOutcome && failure.status !== 401 && failure.code !== "CSRF_INVALID") {
        setGoodsReview(null);
        if (failure.status === 409) onConflict?.();
      }
    } finally {
      attempt.inFlight = false;
      // Notify a newly mounted panel if the session changed while this request was in flight.
      if (getGoodsReview() === attempt) setGoodsReview({ ...attempt });
      setBusy(false);
    }
  }

  return {
    disabled: busy || !!goodsReview,
    start: (body: GoodsReviewAction) => {
      if (user) void send({ body, key: crypto.randomUUID(), actorId: user.userId, route, organizationId });
    },
    feedback: <>
      <ErrorNotice error={error} />
      {pending && !pending.inFlight && <button type="button" className="button secondary" onClick={() => void send(pending)}>Retry same request</button>}
      {(busy || pending?.inFlight) && <p role="status">Saving decision…</p>}
      {saved && <p role="status">Decision saved.</p>}
    </>,
  };
}
