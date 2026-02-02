;; valid_module.wat - Known-good module for baseline testing
;; All functions should execute correctly within resource limits

(module
  ;; Simple memory for testing
  (memory (export "memory") 1)

  ;; Addition function
  (func $add (export "add") (param $a i32) (param $b i32) (result i32)
    (i32.add (local.get $a) (local.get $b))
  )

  ;; Multiplication function
  (func $mul (export "mul") (param $a i32) (param $b i32) (result i32)
    (i32.mul (local.get $a) (local.get $b))
  )

  ;; Factorial (reasonable recursion)
  (func $factorial (export "factorial") (param $n i32) (result i32)
    (if (result i32) (i32.le_s (local.get $n) (i32.const 1))
      (then (i32.const 1))
      (else
        (i32.mul
          (local.get $n)
          (call $factorial (i32.sub (local.get $n) (i32.const 1)))
        )
      )
    )
  )

  ;; Memory write and read
  (func $store_and_load (export "store_and_load") (param $addr i32) (param $val i32) (result i32)
    (i32.store (local.get $addr) (local.get $val))
    (i32.load (local.get $addr))
  )

  ;; Return a constant (for simple testing)
  (func $get_answer (export "get_answer") (result i32)
    (i32.const 42)
  )
)
