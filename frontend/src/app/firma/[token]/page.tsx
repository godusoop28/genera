"use client";

import { use } from "react";
import { SigningPage } from "./SigningPage";

export default function FirmaPage({ params }: PageProps<"/firma/[token]">) {
  const { token } = use(params);
  return <SigningPage token={token} />;
}
