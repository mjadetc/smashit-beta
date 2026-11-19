// One labelled input with an optional hint and its error message underneath.
//   <FormField label="Email" name="email" type="email" value={...} onChange={...} error={errors.email} />
//
// CHANGED in step 2:
//   as="select" or as="textarea" draws a dropdown or a multi-line box instead of an <input>.
//   For a dropdown, put the <option> elements between the tags (they arrive as "children").
//   hint="..." shows a short help text under the label.
// Any extra props (autoComplete, min, max...) are passed straight to the input.
export default function FormField({ label, name, error, hint, as = 'input', children, ...inputProps }) {
  // A variable holding a tag name ('input', 'select'...) can be used as a JSX tag,
  // as long as its name starts with a capital letter. So <Control> becomes <select> etc.
  const Control = as;

  // Ids link the label, hint and error message to the input, so screen readers
  // read "Email, Email is required." when the input is focused.
  const inputId = `field-${name}`;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;

  // aria-describedby can list several ids, separated by spaces.
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  return (
    <div className="field">
      <label htmlFor={inputId}>{label}</label>
      {hint && (
        <p id={hintId} className="field-hint">
          {hint}
        </p>
      )}
      <Control
        id={inputId}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        {...inputProps}
      >
        {children}
      </Control>
      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}