import { useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { useAuth } from '../auth/AuthContext.js';
import FormField from '../components/FormField.jsx';

// The empty form. confirmPassword only exists here: it's never sent to the backend.
const EMPTY_FORM = {
  firstName: '',
  lastName: '',
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
};

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setFieldErrors({});
    setFormError('');

    // The one rule the backend can't check: it never sees the second password.
    if (form.password !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: "The passwords don't match." });
      return;
    }

    setSubmitting(true);
    try {
      // "Destructuring": take confirmPassword out, keep everything else in "details".
      const { confirmPassword: _unused, ...details } = form;
      await register(details);
      navigate('/events', { replace: true });
    } catch (error) {
      // 400 -> messages per field; 409 ("That email is already registered.") -> one message.
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
        <p className="muted">Create your organizer account.</p>

        <form onSubmit={handleSubmit} noValidate>
          {/* Two inputs side by side on wider screens (see .field-row in index.css). */}
          <div className="field-row">
            <FormField
              label="First name"
              name="firstName"
              autoComplete="given-name"
              value={form.firstName}
              onChange={handleChange}
              error={fieldErrors.firstName}
            />
            <FormField
              label="Last name"
              name="lastName"
              autoComplete="family-name"
              value={form.lastName}
              onChange={handleChange}
              error={fieldErrors.lastName}
            />
          </div>
          <FormField
            label="Username"
            name="username"
            autoComplete="username"
            value={form.username}
            onChange={handleChange}
            error={fieldErrors.username}
          />
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
            label="Password (at least 8 characters)"
            name="password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={handleChange}
            error={fieldErrors.password}
          />
          <FormField
            label="Confirm password"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={handleChange}
            error={fieldErrors.confirmPassword}
          />

          {formError && (
            <p className="form-error" role="alert">
              {formError}
            </p>
          )}

          <button type="submit" className="button primary wide" disabled={submitting}>
            {submitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="muted switch-link">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </div>
    </main>
  );
}