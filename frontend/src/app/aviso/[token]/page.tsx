"use client";

import { use } from "react";
import { PrivacySignPage } from "./PrivacySignPage";

export default function AvisoPage({ params }: PageProps<"/aviso/[token]">) {
  const { token } = use(params);
  return <PrivacySignPage token={token} />;
}
