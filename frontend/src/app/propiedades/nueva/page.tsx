"use client";

import { useApi } from "@/components/crm/CrmUi";
import { PropertyForm } from "@/components/crm/PropertyForm";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateMessage } from "@/components/ui/StateMessage";
import { listAdvisors } from "@/lib/api/crm";
import { createProperty } from "@/lib/api/properties";
import { useCan } from "@/lib/permissions";
import { useRouter } from "next/navigation";

export default function NewPropertyPage() {
  const router = useRouter();
  const can = useCan();
  const seesAll = can("LEAD_VIEW_ALL");
  const advisors = useApi(() => (seesAll ? listAdvisors() : Promise.resolve([])), [seesAll]);

  if (!can("PROPERTY_MANAGE")) {
    return (
      <PageContainer title="Nueva propiedad">
        <StateMessage kind="restricted" title="Acceso restringido" description="Tu usuario no puede dar de alta propiedades." />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="Nueva propiedad" subtitle="Se guarda en Genera y se publica en 21Online en el mismo paso.">
      <PropertyForm
        advisors={advisors.data ?? []}
        canChooseAdvisor={seesAll}
        submitLabel="Guardar y publicar en 21Online"
        onCancel={() => router.push("/propiedades")}
        onSubmit={async (data) => {
          const created = await createProperty(data);
          router.push(`/propiedades/${created.id}`);
        }}
      />
    </PageContainer>
  );
}
