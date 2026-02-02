;; fuel_consumer.wat - Tests fuel metering with known consumption
;; Contains a loop that consumes predictable amount of fuel

(module
  (func $consume_fuel (export "consume_fuel") (param $iterations i32) (result i32)
    (local $counter i32)
    (local $result i32)
    (local.set $result (i32.const 0))
    (local.set $counter (i32.const 0))

    (block $break
      (loop $loop
        ;; Check if we've done enough iterations
        (br_if $break (i32.ge_u (local.get $counter) (local.get $iterations)))

        ;; Do some work (add to result)
        (local.set $result (i32.add (local.get $result) (local.get $counter)))

        ;; Increment counter
        (local.set $counter (i32.add (local.get $counter) (i32.const 1)))

        ;; Continue loop
        (br $loop)
      )
    )

    (local.get $result)
  )

  ;; Simple function that consumes minimal fuel
  (func $minimal (export "minimal") (result i32)
    (i32.const 1)
  )
)
