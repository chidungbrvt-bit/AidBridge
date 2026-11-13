import { sortCampaignItems, type CampaignItem } from '@/lib/campaign';

export function GoodsProgressTable({ items }: { items: readonly CampaignItem[] }) {
  return <div className="table-wrap">
    <table className="data-table">
      <caption className="sr-only">Campaign goods progress</caption>
      <thead><tr><th scope="col">Item</th><th scope="col">Unit</th><th scope="col">Target</th><th scope="col">Pledged</th><th scope="col">Received</th><th scope="col">Delivered</th><th scope="col">Confirmed</th></tr></thead>
      <tbody>{sortCampaignItems(items).map(item => <tr key={item.id}>
        <th scope="row">{item.name}</th><td>{item.unit}</td><td>{item.targetQty}</td><td>{item.pledgedQty}</td><td>{item.receivedQty}</td><td>{item.deliveredQty}</td><td>{item.confirmedQty}</td>
      </tr>)}</tbody>
    </table>
  </div>;
}
