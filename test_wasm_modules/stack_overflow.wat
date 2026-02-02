;; stack_overflow.wat - Tests stack overflow handling
;; Deep recursion to exhaust call stack

(module
  (func $recurse (export "recurse") (param $depth i32) (result i32)
    (if (result i32) (i32.gt_s (local.get $depth) (i32.const 0))
      (then
        ;; Recursive call with depth - 1
        (call $recurse (i32.sub (local.get $depth) (i32.const 1)))
      )
      (else
        (i32.const 0)
      )
    )
  )

  (func $trigger_overflow (export "trigger_overflow") (result i32)
    ;; Call with very large depth to overflow stack
    (call $recurse (i32.const 1000000))
  )
)
