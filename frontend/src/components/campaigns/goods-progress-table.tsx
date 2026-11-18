'use client';

import { sortCampaignItems, type CampaignItem } from '@/lib/campaign';
import { useI18n } from '@/components/i18n-provider';

export function GoodsProgressTable({ items }: { items: readonly CampaignItem[] }) {
  const { t } = useI18n();
  return <div className="table-wrap">
    <table className="data-table">
      <caption className="sr-only">{t("Campaign goods progress")}</caption>
      <thead><tr><th scope="col">{t("Item")}</th><th scope="col">{t("Unit")}</th><th scope="col">{t("Target")}</th><th scope="col">{t("Pledged")}</th><th scope="col">{t("Received")}</th><th scope="col">{t("Delivered")}</th><th scope="col">{t("Confirmed")}</th></tr></thead>
      <tbody>{sortCampaignItems(items).map(item => <tr key={item.id}>
        <th scope="row">{item.name}</th><td>{item.unit}</td><td>{item.targetQty}</td><td>{item.pledgedQty}</td><td>{item.receivedQty}</td><td>{item.deliveredQty}</td><td>{item.confirmedQty}</td>
      </tr>)}</tbody>
    </table>
  </div>;
}
