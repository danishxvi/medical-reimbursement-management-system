import { forwardRef, useId } from 'react'
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import { AnimatePresence, motion } from 'motion/react'

interface FieldProps {
  label: string
  hint?: ReactNode
  error?: string
  required?: boolean
  children: (id: string, describedBy: string | undefined) => ReactNode
  className?: string
}

/** Label, control, hint and animated error message in one accessible block. */
export function Field({ label, hint, error, required, children, className }: FieldProps) {
  const id = useId()
  const hintId = hint ? id + '-hint' : undefined
  const errorId = error ? id + '-error' : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  return (
    <div className={['field', error && 'has-error', className].filter(Boolean).join(' ')}>
      <label className="field-label" htmlFor={id}>
        <span>{label}</span>
        {required && <span className="req">Required</span>}
      </label>
      <div className="control-wrap">{children(id, describedBy)}</div>
      {hint && (
        <span className="field-hint" id={hintId}>
          {hint}
        </span>
      )}
      <AnimatePresence initial={false}>
        {error && (
          <motion.span
            key="err"
            className="field-error"
            id={errorId}
            role="alert"
            initial={{ opacity: 0, x: -6 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0 }}
          >
            {error}
          </motion.span>
        )}
      </AnimatePresence>
    </div>
  )
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: ReactNode; error?: string }

export const TextField = forwardRef<HTMLInputElement, InputProps>(function TextField(
  { label, hint, error, required, className, ...rest },
  ref,
) {
  return (
    <Field label={label} hint={hint} error={error} required={required} className={className}>
      {(id, describedBy) => (
        <input
          ref={ref}
          id={id}
          className="control"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          required={required}
          {...rest}
        />
      )}
    </Field>
  )
})

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string
  hint?: ReactNode
  error?: string
  options: { value: string | number; label: string }[]
  placeholder?: string
}

export const SelectField = forwardRef<HTMLSelectElement, SelectProps>(function SelectField(
  { label, hint, error, required, options, placeholder, className, ...rest },
  ref,
) {
  return (
    <Field label={label} hint={hint} error={error} required={required} className={className}>
      {(id, describedBy) => (
        <select
          ref={ref}
          id={id}
          className="control"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          required={required}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      )}
    </Field>
  )
})

type AreaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; hint?: ReactNode; error?: string }

export const TextAreaField = forwardRef<HTMLTextAreaElement, AreaProps>(function TextAreaField(
  { label, hint, error, required, className, ...rest },
  ref,
) {
  return (
    <Field label={label} hint={hint} error={error} required={required} className={className}>
      {(id, describedBy) => (
        <textarea
          ref={ref}
          id={id}
          className="control"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          required={required}
          {...rest}
        />
      )}
    </Field>
  )
})

type CheckProps = InputHTMLAttributes<HTMLInputElement> & { label: ReactNode }

export const Checkbox = forwardRef<HTMLInputElement, CheckProps>(function Checkbox({ label, ...rest }, ref) {
  return (
    <label className="check">
      <input ref={ref} type="checkbox" {...rest} />
      <span>{label}</span>
    </label>
  )
})

interface SegmentedProps<T extends string> {
  name: string
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
  label?: string
}

/** Radio group drawn as joined boxes. */
export function Segmented<T extends string>({ name, value, options, onChange, label }: SegmentedProps<T>) {
  return (
    <div className="field">
      {label && <span className="field-label">{label}</span>}
      <div className="segmented" role="radiogroup" aria-label={label}>
        {options.map((o) => (
          <label key={o.value}>
            <input
              type="radio"
              name={name}
              value={o.value}
              checked={value === o.value}
              onChange={() => onChange(o.value)}
            />
            {o.label}
          </label>
        ))}
      </div>
    </div>
  )
}
