import { Modal } from "@/components/ui/Modal";

interface DocumentPreviewModalProps {
  open: boolean;
  title?: string;
  onClose: () => void;
  pageCount?: number;
}

export function DocumentPreviewModal({
  open,
  title,
  onClose,
  pageCount = 1,
}: DocumentPreviewModalProps) {
  return (
    <Modal open={open} onClose={onClose} title={title} size="md">
      <div className="flex flex-col items-center">
        <div className="flex w-full max-w-sm flex-col items-center gap-3">
          {Array.from({ length: Math.min(pageCount, 2) }).map((_, index) => (
            <div
              key={index}
              className="flex aspect-[3/4] w-full flex-col gap-3 rounded-lg border border-neutral-200 bg-white p-6 shadow-inner"
              style={{
                marginLeft: index > 0 ? "12px" : 0,
                marginTop: index > 0 ? "-88%" : 0,
                zIndex: -index,
              }}
            >
              <div className="h-2.5 w-2/3 rounded bg-neutral-100" />
              <div className="h-2 w-full rounded bg-neutral-100" />
              <div className="h-2 w-full rounded bg-neutral-100" />
              <div className="h-2 w-5/6 rounded bg-neutral-100" />
              <div className="mt-4 h-24 w-full rounded bg-neutral-100" />
              <div className="mt-4 h-2 w-full rounded bg-neutral-100" />
              <div className="h-2 w-full rounded bg-neutral-100" />
              <div className="h-2 w-4/6 rounded bg-neutral-100" />
              <div className="mt-auto h-2 w-1/3 self-end rounded bg-neutral-100" />
            </div>
          ))}
        </div>
        <p className="mt-6 text-xs text-muted">
          Vista previa simulada del documento digitalizado
          {pageCount > 1 ? ` · ${pageCount} páginas combinadas` : ""}.
        </p>
      </div>
    </Modal>
  );
}
