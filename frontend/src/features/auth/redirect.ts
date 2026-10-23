// Destino tras el login: solo rutas internas ("/algo"). Evita redirecciones abiertas
// a otros dominios ("//evil.com", "/\evil.com", "https://...")
export function safeRedirect(target: unknown): string {
  if (typeof target !== 'string' || !target.startsWith('/')) return '/'
  if (target.startsWith('//') || target.startsWith('/\\')) return '/'
  return target
}
