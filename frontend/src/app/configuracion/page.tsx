import { PageContainer } from "@/components/layout/PageContainer";
import { AccountSettingsForm } from "@/components/settings/AccountSettingsForm";
import { ChangePasswordCard } from "@/components/settings/ChangePasswordCard";
import { FeasibilityPromptsCard } from "@/components/settings/FeasibilityPromptsCard";
import { FontSizeControl } from "@/components/settings/FontSizeControl";
import { NotificationSettingsCard } from "@/components/settings/NotificationSettingsCard";
import { PropertyCatalogCard } from "@/components/settings/PropertyCatalogCard";
import { ThemeSwitcher } from "@/components/settings/ThemeSwitcher";
import { VisitInstructionsCard } from "@/components/settings/VisitInstructionsCard";

export default function ConfiguracionPage() {
  return (
    <PageContainer title="Mi perfil y configuración" subtitle="Tu cuenta, tu contraseña y las preferencias de apariencia (estas se guardan en este navegador).">
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="flex flex-col gap-6">
          <AccountSettingsForm />
          <ChangePasswordCard />
          <ThemeSwitcher />
          <FontSizeControl />
        </div>
        <div className="flex flex-col gap-6">
          <NotificationSettingsCard />
          <VisitInstructionsCard />
          <PropertyCatalogCard />
        </div>
        <div className="lg:col-span-2">
          <FeasibilityPromptsCard />
        </div>
      </div>
    </PageContainer>
  );
}
