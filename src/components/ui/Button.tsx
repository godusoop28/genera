import { cn } from "@/lib/utils";
import { type ButtonHTMLAttributes, forwardRef } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

const variantClasses: Record<Variant, string> = {
  // Oro con texto Obsessed Grey: mejor contraste que oro + blanco (ver AGENTS/§80).
  primary:
    "bg-gold text-obsessed hover:bg-dark-gold focus-visible:ring-gold/40 shadow-sm disabled:opacity-50",
  secondary:
    "bg-card text-obsessed border border-border hover:bg-app-bg focus-visible:ring-obsessed/15 disabled:opacity-50",
  ghost: "bg-transparent text-obsessed hover:bg-app-bg focus-visible:ring-obsessed/15",
  danger:
    "bg-card text-danger-text border border-danger-text/30 hover:bg-danger-bg focus-visible:ring-red-200",
};

const sizeClasses: Record<Size, string> = {
  sm: "text-sm px-3 py-1.5 gap-1.5",
  md: "text-sm px-4 py-2.5 gap-2",
  lg: "text-base px-5 py-3 gap-2",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = "primary", size = "md", ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={cn(
        "inline-flex items-center justify-center rounded-xl font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 disabled:cursor-not-allowed",
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    />
  );
});
