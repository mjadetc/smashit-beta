// One labelled input with its error message underneath.
//   <FormField label="Email" name="email" type="email" value={...} onChange={...} error={errors.email} />
// Any extra props (autoComplete, autoFocus...) are passed straight to the <input>.
export default function FormField({ label, name, error, ...inputProps }) {
  // Ids link the label and the error message to the input, so screen readers
  // read "Email, Email is required." when the input is focused.
  const inputId = `field-${name}`;
  const errorId = `${inputId}-error`;

  return (
    <div className="field">
      <label htmlFor={inputId}>{label}</label>
      <input
        id={inputId}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        {...inputProps}
      />
      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}