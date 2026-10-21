"use client";

import { use } from "react";
import { AccountLinkPage } from "./AccountLinkPage";

export default function AccesoPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params);
  return <AccountLinkPage token={token} />;
}
