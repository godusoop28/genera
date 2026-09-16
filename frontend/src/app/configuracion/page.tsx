import { PageContainer } from "@/components/layout/PageContainer";
import { AccountSettingsForm } from "@/components/settings/AccountSettingsForm";
import { DataResetCard } from "@/components/settings/DataResetCard";
import { FontSizeControl } from "@/components/settings/FontSizeControl";
import { NotificationSettingsCard } from "@/components/settings/NotificationSettingsCard";
import { SecuritySettingsCard } from "@/components/settings/SecuritySettingsCard";
import { ThemeSwitcher } from "@/components/settings/ThemeSwitcher";

export default function ConfiguracionPage() {
  return (
    <PageContainer
      title="Configuración"
      subtitle="Preferencias de apariencia y de la cuenta. Los cambios se guardan en este navegador."
    >
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="flex flex-col gap-6">
          <ThemeSwitcher />
          <FontSizeControl />
        </div>
        <div className="flex flex-col gap-6">
          <AccountSettingsForm />
          <NotificationSettingsCard />
          <SecuritySettingsCard />
          <DataResetCard />
        </div>
      </div>
    </PageContainer>
  );
}
