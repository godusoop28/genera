import { Modal } from "@/components/ui/Modal";
import type { ReceivedDocument } from "@/types/expediente";

interface DocumentPreviewModalProps {
  document: ReceivedDocument | null;
  onClose: () => void;
}

export function DocumentPreviewModal({ document, onClose }: DocumentPreviewModalProps) {
  return (
    <Modal open={document !== null} onClose={onClose} title={document?.name} size="md">
      <div className="flex flex-col items-center">
        <div className="flex aspect-[3/4] w-full max-w-sm flex-col gap-3 rounded-lg border border-border bg-white p-6 shadow-inner">
          <div className="h-2.5 w-2/3 rounded bg-app-bg" />
          <div className="h-2 w-full rounded bg-app-bg" />
          <div className="h-2 w-full rounded bg-app-bg" />
          <div className="h-2 w-5/6 rounded bg-app-bg" />
          <div className="mt-4 h-24 w-full rounded bg-app-bg" />
          <div className="mt-4 h-2 w-full rounded bg-app-bg" />
          <div className="h-2 w-full rounded bg-app-bg" />
          <div className="h-2 w-4/6 rounded bg-app-bg" />
          <div className="mt-auto h-2 w-1/3 self-end rounded bg-app-bg" />
        </div>
        <p className="mt-4 text-xs text-muted">
          Vista previa simulada del documento digitalizado.
        </p>
      </div>
    </Modal>
  );
}
