"use client";

import { useState } from "react";
import { useSession } from "@/components/session-provider";
import { describeError, ErrorNotice, type ErrorInfo } from "@/components/cases/shared";
import type { GoodsReviewAction } from "@/lib/goods";
import type { PendingWrite } from "@/lib/session-work";

export function useGoodsReviewWrite(resource: string, onSaved: () => void, onConflict?: () => void) {
  const { api, user, work } = useSession();
  const scope = `${user?.userId}:${resource}`;
  const [pending, setPending] = useState(() => work.goodsReviews.get(scope));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [saved, setSaved] = useState(false);

  async function send(attempt: PendingWrite<GoodsReviewAction>) {
    const previous = work.goodsReviews.get(scope);
    if (attempt.inFlight || (previous && previous !== attempt)) return;
    attempt.inFlight = true;
    work.goodsReviews.set(scope, attempt);
    setPending(attempt);
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      if (previous) {
        const current = await api.me();
        if (current.userId !== user?.userId || work.goodsReviews.get(scope) !== attempt) return;
      }
      const command = attempt.body;
      switch (command.action) {
        case "confirm": await api.confirmDelivery(command.id, command.body, attempt.key); break;
        case "dispute": await api.openCaseDispute(command.id, command.body, attempt.key); break;
        case "resolve": await api.resolveDispute(command.id, command.body, attempt.key); break;
        case "close": await api.closeCampaign(command.id, attempt.key); break;
      }
      if (work.goodsReviews.get(scope) !== attempt) return;
      work.goodsReviews.delete(scope);
      setPending(undefined);
      setSaved(true);
      onSaved();
    } catch (cause) {
      if (work.goodsReviews.get(scope) !== attempt) return;
      const failure = describeError(cause);
      setError(failure);
      if (!failure.unknownOutcome && failure.code !== "CSRF_INVALID") {
        work.goodsReviews.delete(scope);
        setPending(undefined);
        if (failure.status === 409) onConflict?.();
      }
    } finally {
      attempt.inFlight = false;
      setBusy(false);
    }
  }

  return {
    disabled: busy || !!pending,
    start: (body: GoodsReviewAction) => void send({ body, key: crypto.randomUUID() }),
    feedback: <>
      <ErrorNotice error={error} />
      {pending && !busy && <button type="button" className="button secondary" onClick={() => void send(pending)}>Retry same request</button>}
      {busy && <p role="status">Saving decision…</p>}
      {saved && <p role="status">Decision saved.</p>}
    </>,
  };
}
