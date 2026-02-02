;; infinite_loop.wat - Tests fuel exhaustion and epoch interruption
;; This module contains an infinite loop that should be terminated by resource limits

(module
  (func $loop_forever (export "loop_forever")
    (loop $infinite
      (br $infinite)
    )
  )

  (func $add (export "add") (param $a i32) (param $b i32) (result i32)
    ;; Valid function for baseline testing
    (i32.add (local.get $a) (local.get $b))
  )
)
