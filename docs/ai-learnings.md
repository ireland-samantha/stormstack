
## AI-Assisted Development: Learnings

### What Worked

- **Enforcing a workflow:** TDD kept the model on task
- **Continuous refactoring:** Regular SOLID/clean code evaluations
- **Knowledge persistence:** Session summaries in `llm-learnings/`
- **Defensive programming:** Immutable records, validation in constructors

### What Didn't Work

- **Trusting generated code blindly:** Uncorrected mistakes compounded
- **Ambiguous prompts:** Vague requests produced vague implementations
- **Skipping test reviews:** Generated tests sometimes asserted wrong behavior

### Key Insight

AI accelerates development but requires human rigor. The model produces code you must understand—if you can't explain why it works, you can't fix it when it breaks.
