import { createContext, useContext } from 'react';

// A React "context" is a value that any component inside <AuthProvider> can read,
// without passing it down through every component's props.
// It holds: { user, checking, login, register, logout } (see AuthProvider.jsx).
export const AuthContext = createContext(null);

// The hook screens use:  const { user, logout } = useAuth();
export function useAuth() {
  const auth = useContext(AuthContext);
  if (auth === null) {
    // A clear message instead of "cannot read properties of null" somewhere later.
    throw new Error('useAuth() must be used inside <AuthProvider>.');
  }
  return auth;
}