import { describe, expect, it } from "vitest";

// A deliberately failing assertion. This file exists only to prove that a red test makes the pull
// unmergeable even with an admin override, and it is deleted by the commit that closes the probe.
describe("DoD-4 enforcement probe", () => {
  it("fails on purpose", () => {
    expect(1 + 1).toBe(3);
  });
});
