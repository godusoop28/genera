"use client";

import { use } from "react";
import { PublicPortal } from "./PublicPortal";

export default function CargaLinkPage({ params }: PageProps<"/carga/[linkId]">) {
  const { linkId } = use(params);
  return <PublicPortal token={linkId} />;
}
