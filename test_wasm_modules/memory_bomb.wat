;; memory_bomb.wat - Tests memory limit enforcement
;; Attempts to allocate maximum memory, should be blocked by limits

(module
  ;; Start with 1 page (64KB), attempt to grow to max
  (memory (export "memory") 1)

  (func $allocate_max (export "allocate_max") (result i32)
    ;; Try to grow memory by 1000 pages (64MB)
    ;; Should fail due to memory limits
    (memory.grow (i32.const 1000))
  )

  (func $get_memory_size (export "get_memory_size") (result i32)
    (memory.size)
  )
)
