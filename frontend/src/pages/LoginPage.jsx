import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';

import { useAuth } from '../auth/AuthContext.js';
import FormField from '../components/FormField.jsx';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // A "controlled form": React state holds what's typed, and each input shows that state.
  const [form, setForm] = useState({ email: '', password: '' });
  // fieldErrors = messages under each input; formError = one message above the button.
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  // true while waiting for the backend, so the button can't be pressed twice.
  const [submitting, setSubmitting] = useState(false);

  // One change handler for every input: the input's "name" says which field to update.
  function handleChange(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    // A <form> normally reloads the whole page on submit; we handle it in JavaScript instead.
    event.preventDefault();
    setFieldErrors({});
    setFormError('');
    setSubmitting(true);

    try {
      await login(form.email, form.password);
      // Back to the page they wanted before being sent here, or My Events.
      navigate(location.state?.from ?? '/events', { replace: true });
    } catch (error) {
      // 400 -> messages per field; anything else (401 wrong password...) -> one message.
      setFieldErrors(error.fieldErrors ?? {});
      if (Object.keys(error.fieldErrors ?? {}).length === 0) {
        setFormError(error.message);
      }
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <div className="card auth-card">
        <h1 className="brand">SmasHIT</h1>
        <p className="muted">Log in to organize your badminton events.</p>

        {/* noValidate: let the backend's messages be the only validation, so the
            rules (and their wording) are written in one place. */}
        <form onSubmit={handleSubmit} noValidate>
          <FormField
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={handleChange}
            error={fieldErrors.email}
          />
          <FormField
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={handleChange}
            error={fieldErrors.password}
          />

          {/* role="alert" makes screen readers announce the message when it appears. */}
          {formError && (
            <p className="form-error" role="alert">
              {formError}
            </p>
          )}

          <button type="submit" className="button primary wide" disabled={submitting}>
            {submitting ? 'Logging in…' : 'Log in'}
          </button>
        </form>

        <p className="muted switch-link">
          New to SmasHIT? <Link to="/register">Create an account</Link>
        </p>
      </div>
    </main>
  );
}