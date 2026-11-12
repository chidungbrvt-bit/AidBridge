import { DonorRoutes } from '@/components/donor/donor-routes';
export default function DonorLayout({ children }: { children: React.ReactNode }) {
  return <>{children}<DonorRoutes/></>;
}
