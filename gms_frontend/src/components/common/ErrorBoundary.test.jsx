import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ErrorBoundary from './ErrorBoundary';

/**
 * The crash class that proved the need for this boundary: a list endpoint that answered
 * `204` became `null` in the request layer, `data: classrooms = []` did not default on
 * `null`, and `classrooms.map` threw while rendering — taking every page behind it with
 * it, because nothing in this SPA caught a render error.
 */
const Exploding = () => {
  throw new Error('classrooms is not iterable');
};

describe('ErrorBoundary', () => {
  it('keeps a render crash to the screen that caused it', () => {
    // React logs the error it caught; the silence here would hide the failure from the
    // reader of the test output.
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary>
        <Exploding />
      </ErrorBoundary>
    );

    expect(screen.getByText('This screen could not be displayed')).toBeInTheDocument();
    expect(screen.getByText(/classrooms is not iterable/)).toBeInTheDocument();

    errorSpy.mockRestore();
  });

  it('offers a way back rather than a blank page', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary>
        <Exploding />
      </ErrorBoundary>
    );

    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Back to dashboard' })).toBeInTheDocument();

    errorSpy.mockRestore();
  });

  it('renders its children when nothing throws', () => {
    render(
      <ErrorBoundary>
        <div>the page that works</div>
      </ErrorBoundary>
    );

    expect(screen.getByText('the page that works')).toBeInTheDocument();
    expect(screen.queryByText('This screen could not be displayed')).not.toBeInTheDocument();
  });

  it('renders the children again after the caller recovers the screen', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const user = userEvent.setup();

    let broken = true;
    const Sometimes = () => {
      if (broken) {
        throw new Error('the data was not an array');
      }
      return <div>the screen recovered</div>;
    };

    render(
      <ErrorBoundary>
        <Sometimes />
      </ErrorBoundary>
    );

    expect(screen.getByText('This screen could not be displayed')).toBeInTheDocument();

    broken = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(screen.getByText('the screen recovered')).toBeInTheDocument();

    errorSpy.mockRestore();
  });
});
