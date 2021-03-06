(ns recursely.ccore-test
  (:refer-clojure :exclude [pop peek replace remove])
  (:use recursely.ccore)
  (:import (recursely.types Value Call Fn))
  (:import (clj_utils.stackp DefaultStack+))
  (:use  
        [clj-utils [coll :only [subsq]]
                   [stackp] 
                   [debug :only [debug-info-1 debug-info]]]
        clojure.test
        [robert.hooke :as h]))

(def s+ (DefaultStack+.))

;; Do not use, for some reason robert.hooke transforms
;; func names ending with ?
(def DEBUG-play-test false)
(def DEBUG-play-test-basic false)

(declare same-state adapted-counter)

(if DEBUG-play-test (do
#_(h/add-hook #'next-state #'debug-info)
(h/add-hook #'same-state #'debug-info-1)
#_(h/add-hook #'adapted-counter #'debug-info)))

(defn bstart
[ stack pos]
  (peek-r s+ stack 0 pos))

(defn bend
[ stack pos ]
  (peek-r s+ stack (inc pos)))


(defn val=
[ v1 v2 ]
  (and 
    (= Value (class v1))
    (= Value (class v2))
    (= (.value v1) (.value v2))))

(defn call=
[ c1 c2 ]
  (and
    (= Call (class c1))
    (= Call (class c2))
    (= (.fn c1) (.fn c2))
    (= (.numargs c1) (.numargs c2))))

(defn fn=
[ f1 f2 ]
  (and
    (= Fn (class f1))
    (= Fn (class f2))
    (= (.fn f1) (.fn f2))
    (= (.numargs f1) (.numargs f2))))

(defn same-state 
[ [stack pos] [estack epos] ]
  (let [ siz (count stack) esiz (count estack) ]
  (and 
     (= esiz siz)
     (= epos pos)
     (->> (map #(or (= %1 %2) (call= %1 %2) (val= %1 %2) (fn= %1 %2)) stack estack) 
         (every? #(true? %))))))


(deftest same-state-test
  (testing "..testing the testers...Should verify correctly that two stacks have the same content
            including equivalent Calls and Values"
    (is (same-state [[1 2 3 (Value. :a) 4 5] 3] [[1 2 3 (Value. :a) 4 5] 3]))
    (is (same-state [[1 2 (Fn. + 2) 3] 0] [[1 2 (Fn. + 2) 3] 0]))
    (is (same-state [[1 (Value. :b) 2 (Fn. - 2)] 1] [[1 (Value. :b) 2 (Fn. - 2)] 1]))
    (is (not (same-state [[1 (Fn. odd? 1) 2] 1] [[1 (Fn. even? 1) 2] 2])))))


(deftest hval-test 
  (testing "TO REDO, USE SAME-STATE INSTEAD hval should hoist a value in the correct posittion with a Value type"
    (let [ stack [1 2 3 4 5] 
           [tested pos] (hval [stack 2] :a) 
           [tested2 pos2] (hval [stack 0] :a)
           [tested3 pos3] (hval [stack 4] :a)
           [tested4 pos4] (hval [[] 0] :a) 
         ]
       (is (same-state [[1 2 (Value. :a) 3 4 5] 3] [tested pos]))
       (is (same-state [[(Value. :a) 1 2 3 4 5] 1] [tested2 pos2]))
       (is (same-state [[1 2 3 4 (Value. :a) 5] 5] [tested3 pos3]))
       (is (same-state [[(Value. :a)] 1] [tested4 pos4])))))


(deftest hfn-test
  (testing "hfn should hoist a fn call in the correct position with a Call type"
    (let [ stack [1 2 3 4 5] 
            [tested pos] (hfn [stack 2] + 2 10 11) 
            [tested2 pos2] (hfn [stack 5] max 2) 
            [tested3 pos3] (hfn [[] 0] + 3 1 2)

            ;; chained hfns
            [tested4 pos4] (-> (hval [stack 3] 10)
                               (hfn + 2 11))
          ]
      (is (same-state [tested pos] [[1 2 (Value. 10) (Value. 11) (Fn. + 2) 3 4 5] 5]))
      (is (same-state [tested2 pos2] [[1 2 3 4 5 (Fn. max 2)] 6] ))
      (is (same-state [tested3 pos3] [[(Value. 1) (Value. 2) (Fn. + 3)] 3]))
      
      ;; chained hfns 
      (is (same-state [tested4 pos4] [[1 2 3 (Value. 10) (Value. 11) (Fn. + 2) 4 5] 6]))

      ;; rewind
      (is (same-state [tested4 3] (rewind [tested4 pos4] 3))))))


(deftest newstack-test
  (testing "newstack should yield a [ Value1, Value2,...Call] stack pointing at the last element"
     (is (same-state [[(Value. 1) (Value. 2) (Call. + 2)] 2] (newstack + 1 2)))))


(deftest pop-call-test
  (testing "pop-call should remove a Call and its arguments from the stack and reset the 
            cursor to the element following the Call position, and yield the resulting stack/pos"
     (let [ 
            estack [1 2 3 4 5]  
            estack2 []

            [stack pos] [[1 2 3 (Value. 2) (Value. 1) (Call. + 2) 4 5] 5]
            epos 3
            [astack apos] (pop-call stack pos 2) 
            
            [stack2 pos2] [[(Value. 2) (Value. 1) (Value. 100) (Call. max 3) 1 2 3 4 5] 3]
            epos2 0
            [astack2 apos2] (pop-call stack2 pos2 3)

            [stack3 pos3] [[1 2 3 4 5 (Value. 1) (Call. odd? 1)] 6]
            epos3 5
            [astack3 apos3] (pop-call stack3 pos3 1)

            [stack4 pos4] [[(Value. 3) (Value. 2) (Value. 1) (Call. * 3)] 3]
            epos4 0
            [astack4 apos4] (pop-call stack4 pos4 3)
            ]
        (is (same-state [estack epos] [astack apos]))

        ;; NOT a typo: we reuse estack several times..
        (is (same-state [estack epos2] [astack2 apos2]))
        (is (same-state [estack epos3] [astack3 apos3]))
        (is (same-state [estack2 epos4] [astack4 apos4])))))


(deftest next-state-test_hfn
  (testing "next-state should handle stack updates from calls to hval/hcall by taking the lead 
            from the cursored frame, which means incrementing the cursor if a value if found,
            or invoking a client function after popping the call and its args off the stack"

    (let [ 
            [stack pos] [[1 2 3 (Value. 11) (Value. 10) (Fn. + 2) 4 5] 5] 
            [stack1 pos1] [[(Value. 1000) (Fn. even? 1) 1 2 3 4 5] 1]
            [stack2 pos2] [[1 2 3 4 5 (Value. :a) (Value. :b) (Value. :c) (Fn. #(vector %1 %2 %3) 3)] 8]
            [stack3 pos3] [[(Value. 3) (Value. 2) (Fn. * 2)] 2]
        ]
    
        (is (same-state [[1 2 3 (Value. 21) 4 5] 3] (next-state stack pos)))
        (is (same-state [[(Value. true) 1 2 3 4 5] 0] (next-state stack1 pos1))) 
        (is (same-state [[1 2 3 4 5 (Value. [:a :b :c])] 5] (next-state stack2 pos2))) 
        (is (same-state [[(Value. 6)] 0] (next-state stack3 pos3)))))) 


(deftest next-state-test_hcall
  (testing "next-state should invoke the function placed at pos where it will update the stack further"
    (let [
            client (fn [stack pos one two] (-> (hval [stack pos] (+ one two)) (rewind pos)))
            [stack pos] [[1 2 3 (Value. 11) (Value. 10) (Call. client 2) 4 5] 5]
         ]
        (is (same-state [[1 2 3 (Value. 21) 4 5] 3] (next-state stack pos)))
        )))


(deftest extract-fn-test
  (testing "extract-fn should extract fn info"
    (let [
            client (fn [stack pos one two] (-> (hval [stack pos] (+ one two)) (rewind pos)))
            [stack pos] [[1 2 3 (Value. 11) (Value. 10) (Call. client 2) 4 5] 5]
            expected [[[1 2 3 4 5] 3] client [10 11]]
        ]
        (is (same-state expected (recursely.ccore/extract-fn stack pos)))))) 

(deftest end?-test
  (testing "end? should detect end-of-recursion with a single Value on the stack"
    (is (end? [(Value. 1)] 0))))


;;;;;;;;;;;;;;;;;;;;;
;;   Simple but nested, recursive counter 
;;;;;;;;;;;;;;;;;;;;;
(defn counter
[ coll ]
    (if (empty? coll) 0
        (+ 1 (counter (rest coll)))))


(defn adapted-counter 
[stack pos coll] 
    (if (empty? coll) 
        (hval [stack pos] 0)
          (-> (hparam [stack pos] 1)
            
              ;;this used only when not included with hcall
              (hparam (rest coll))
              #_(hcall adapted-counter 1)

              ;; note the 2 extra params when using a wrapper to account for stack & pos
              (hcall (fn [arg1 arg2 arg3] (adapted-counter arg1 arg2 arg3)) 1)
              #_(hcall adapted-counter 1 (rest coll))
              (hfn + 2)
              (rewind pos))))

    
(deftest play-test
  (testing "Should hoist an adapted nested recursive function and play it for the correct result"
    (let [ stack [:a :b :c]
           stack1 [:a]
           stack2 []
           exp (counter stack) ;; 3
           exp1 (counter stack1) ;;1
           exp2 (counter stack2) ;;0
           actual (play adapted-counter stack) 
           act1   (play adapted-counter stack1)
           act2   (play adapted-counter stack2) ]
        ;; make sure the unapdated impl. works at least
        (is (and (= exp 3) (= exp1 1) (= exp2 0)))
        (is (= 3 actual))
        (is (= 1 act1))
        (is (= 0 act2)))))


(defn recursive-odd?
[ x ]
  (cond (= 1 x) true
        (= 0 x) false
    :else 
        (recursive-odd? (- x 2))))

(defn adapted-recursive-odd?
[stack pos x]
    (cond (= 1 x) (hval [stack pos] true)
          (= 0 x) (hval [stack pos] false)
        :else
            #_(-> (hcall [stack pos] adapted-recursive-odd? 1 (- x 2))
                    (rewind pos))

            ;; DSL should yield:
            (-> (hparam [stack pos] (- x 2))
                (hcall (fn [arg1 arg2 arg3] (adapted-recursive-odd? arg1 arg2 arg3)) 1)
                (rewind pos))))
        
(if DEBUG-play-test-basic (do
(h/add-hook #'adapted-recursive-odd? #'debug-info-1)
))

(deftest play-test-basic
  (testing "Should work with non-nested, straight array recursion as well"
    (is (= true (recursive-odd? 1)))
    (is (= true (recursive-odd? 11)))
    (is (= false (recursive-odd? 0)))
    (is (= false (recursive-odd? 10)))
    (is (= true (play adapted-recursive-odd? 1)))
    (is (= true (play adapted-recursive-odd? 7)))
    (is (= false (play adapted-recursive-odd? 0)))
    (is (= false (play adapted-recursive-odd? 6)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                         ;;
;; Mutually Recursive Funcs                ;;
;;                                         ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;a value generating version of the example in Listing 3-1 of Clojure In Action 2ed
(declare hat)
(defn cat 
([n acc]
(do #_(println "CAT: n=" n ", acc=" acc)
  (if (zero? n) acc
    (let [ term (if (= 0 (rem n 2)) n 0) ]
        (hat (dec n) (+ term acc))))))
([n] (cat n 0)))

(defn hat 
([n acc]
(do #_(println "HAT: n=" n ", acc=" acc)
  (if (zero? n) acc
    (let [ term (if (= 0 (rem n 3)) n 0) ]
        (cat (dec n) (+ term acc))))))
([n] (hat n 0)))

(declare adapted-hat)

(defn adapted-cat 
([ stack pos n acc]
(do #_(println "ADAPTED-CAT: n=" n ", acc=" acc)
    (if (zero? n) (hval [stack pos] acc)
      (let [ term (if (= 0 (rem n 2)) n 0) ]
        #_(-> (hcall [stack pos] adapted-hat 2 (dec n) (+ term acc)) (rewind pos))

          ;; DSL macro should yield:
          (-> (hparam [stack pos] (dec n))
              (hparam term)
              (hparam acc)
              (hfn (fn [arg1 arg2] (+ arg1 arg2)) 2)
              (hcall (fn [arg1 arg2 arg3 arg4] (adapted-hat arg1 arg2 arg3 arg4)) 2)
              (rewind pos))))))
([ stack pos n]
  (adapted-cat stack pos n 0)))

(defn adapted-hat
([ stack pos n acc]
(do #_(println "ADAPTED-HAT: n=" n ", acc=" acc)
  (if (zero? n) (hval [stack pos] acc)
     (let [ term (if (= 0 (rem n 3)) n 0) ]
        #_(-> (hcall [stack pos] adapted-cat 2 (dec n) (+ term acc)) (rewind pos))
        
        ;;DSL macro should yield:
        (-> (hparam [stack pos] (dec n))
            (hparam term)
            (hparam acc)
            (hfn (fn [arg1 arg2] (+ arg1 arg2)) 2)
            (hcall (fn [arg1 arg2 arg3 arg4] (adapted-cat arg1 arg2 arg3 arg4)) 2)
            (rewind pos))))))
([ stack pos n ]
  (adapted-hat stack pos n 0)))


(deftest two-mutually-recursive-funcs-test
  (testing "adapted hat/cat should yield the same value as their counterparts"
    (let [ 
           exp-1 (hat 3)  exp-1a (cat 3)
           act-1 (play adapted-hat 3) act-1a (play adapted-cat 3) 
           
           exp-2 (hat 23) exp-2a (cat 23)
           act-2 (play adapted-hat 23) act-2a (play adapted-cat 23)
        ]
        (is (= exp-1 act-1))
        (is (= exp-1a act-1a))
        (is (= exp-2 act-2))
        (is (= exp-2a act-2a)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;
;;;;   Both Mutually Recursive and Nested
;;;;   Hofstadter sequences, impl taken from Programming Clojure (S. Holloway)
;;;;
;;;;    Definition:    
;;;;
;;;;    F(0) = 1; M(0) = 0
;;;;    F(n) = n - M(F(n-1)), n > 0
;;;;    M(n) = n - F(M(n-1)), n > 0
;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(declare female)
(defn male
[ n ]
  (if (zero? n) 0
    (- n (female (male (dec n))))))

(defn female 
[ n ]
(if (zero? n) 1
    (- n (male (female (dec n))))))

(declare adapted-female)
(defn adapted-male
[ stack pos n ]
  (if (zero? n) (hval [stack pos] 0)
      #_(-> (hparam [stack pos] n)
          (hcall adapted-male 1 (dec n))
          (hcall adapted-female 1)
          (hfn - 2)
          (rewind pos))

      ;; DSL macro should yield:
      (-> (hparam [stack pos] n)
          (hparam (dec n))
          (hcall (fn [arg1 arg2 arg3] (adapted-male arg1 arg2 arg3)) 1)
          (hcall (fn [arg1 arg2 arg3] (adapted-female arg1 arg2 arg3)) 1)
          (hfn (fn [arg1 arg2] (- arg1 arg2)) 2)
          (rewind pos))))

(defn adapted-female
[ stack pos n ]
  (if (zero? n) (hval [stack pos] 1)
    #_(-> (hparam [stack pos] n)
        (hcall adapted-female 1 (dec n))
        (hcall adapted-male 1)
        (hfn - 2)
        (rewind pos)
        )

    (-> (hparam [stack pos] n)
        (hparam (dec n))
        (hcall (fn [arg1 arg2 arg3] (adapted-female arg1 arg2 arg3)) 1)
        (hcall (fn [arg1 arg2 arg3] (adapted-male arg1 arg2 arg3)) 1)
        (hfn (fn [arg1 arg2] (- arg1 arg2)) 2)
        (rewind pos))))


(deftest two-mutually-recursive-nested-test
  (testing "Adaptations of Hofstadter male/female should yield the same values as their counterparts"
    (let [
            exp-1 (male 1) exp-1a (female 1)
            act-1 (play adapted-male 1) act-1a (play adapted-female 1)

            exp-2 (male 10) exp-2a (female 10)
            act-2 (play adapted-male 10) act-2a (play adapted-female 10)

            exp-3 (male 25) exp-3a (female 25)
            act-3 (play adapted-male 25) act-3a (play adapted-female 25)

            exp-4 (map #(vector (male %) (female %)) (range 1 10))
            act-4 (map #(vector (play adapted-male %) (play adapted-female %)) (range 1 10))
         ]
       (is (= exp-1 act-1))
       (is (= exp-1a act-1a))
       (is (= exp-2 act-2))
       (is (= exp-2a act-2a))
       (is (= exp-3 act-3))
       (is (= exp-3a act-3a))
       (is (= exp-4 act-4)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;
;;   Knapsack 1/0 using Wikipedia definition, 
;;   see
;;   http://en.wikipedia.org/wiki/Knapsack_problem#0.2F1_Knapsack_Problem
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cost [ t ] (first t))
(defn value [ t ] (second t))

(defn KS [ coll, capacity ]
  (if (empty? coll) 0
    (let [ [c v] (-> (first coll) (#(vector (cost %) (value %)))) 
           tail (rest coll) ]
       (if (-> c (> capacity))
           (KS tail capacity)
            (max (KS tail capacity)  (+ v (KS tail (- capacity c)))))))) 


(defn adapted-KS [ stack pos coll capacity ]
  (if (empty? coll) (hval [stack pos] 0)
    (let [ [c v] (-> (first coll) (#(vector (cost %) (value %)))) 
           tail (rest coll) ]
         (if (-> c (> capacity))
            #_(-> (hcall [stack pos] adapted-KS 2 tail capacity) (rewind pos))
            #_(-> (hcall [stack pos] adapted-KS 2 tail capacity) 
                (hparam v)
                (hcall adapted-KS 2 tail (- capacity c))
                (hfn + 2)
                (hfn max 2)
                (rewind pos))
               
               ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
               ;;                                                   ;;
               ;;  below is the equivalent of what a DSL            ;;
               ;;  macro should yield:                              ;; 
               ;;                                                   ;;
               ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

            (-> (hparam [stack pos] tail)
                (hparam capacity)
                (hcall (fn [arg1 arg2 arg3 arg4] (adapted-KS arg1 arg2 arg3 arg4)) 2)
                (rewind pos)) 

            (-> (hparam [stack pos] tail)
                (hparam capacity)
                (hcall #(adapted-KS %1 %2 %3 %4) 2)
                (hparam v)
                (hparam tail)
                (hparam (- capacity c))
                (hcall #(adapted-KS %1 %2 %3 %4) 2)
                (hfn #(+ %1 %2) 2)
                (hfn #(max %1 %2) 2)
                (rewind pos))))))

(deftest single-mutually-recursive-nested-knapsack-test
  (testing "Adaptation of knapsack 1/0 should yield the same value as its counterpart"
    (let [
            bag [[4 10] [12 4] [1 2] [1 1] [2 2]]
            exp (map #(KS bag %) (range 0 20))
            act (map #(play adapted-KS bag %) (range 0 20))

            bag2  [[23 92] [31 57] [29 49] [44 68] [53 60] [38 43] 
                    [63 67] [85 84] [89 87] [82 72] ]
            exp-1 (map #(KS bag2 %) [100 136 165 200])
            act-1 (map #(play adapted-KS bag2 %) [100 136 165 200])
        ]
       (is (= exp act))
       (is (= exp-1 act-1 )))))


(defn recursive-and
[ & args ]
  (if (empty? args) true
    (and (first args) (apply recursive-and (rest args)))))

(deftest recursive-and-test
  (testing "recursive-end simple test"
     (is (not (recursive-and true false true)))))


(defn adapted-recursive-and
[ stack pos & args ]
  (if (empty? args) (hval [stack pos] true)
    #_(-> (apply hcall [stack pos] adapted-recursive-and (count (rest args)) (rest args))
        (hfn #(and %1 %2)  2 (first args))
        (rewind pos))
      
       (-> (hparam [stack pos] (first args))
           (hparam-list (rest args))

            ;; either of the next two forms is OK, maybe 
            ;; the second is preferable within DSL macro impl
           #_(hcall adapted-recursive-and (count (rest args)))
           (hcall (fn [& more] (apply adapted-recursive-and more)) (count (rest args)))

           (hfn #(and %1 %2) 2)
           (rewind pos))))


(defn count-args
[ & args ]
  (if (empty? args) 0
    (+ 1 (apply count-args (rest args)))))


(defn adapted-count-args
[ stack pos & args ]
  (if (empty? args) (hval [stack pos] 0)
    #_(-> (apply hcall [stack pos] adapted-count-args (-> (count args) dec) (rest args))
        (hfn + 2 1)
        (rewind pos))

    ;; DSL macro should yield:
    (-> (hparam [stack pos] 1)
        (hparam-list (rest args))
        #_(hcall adapted-count-args (count (rest args)))
        (hcall (fn [& more] (apply adapted-count-args more)) (count (rest args)))
        (hfn #(+ %1 %2) 2)
        (rewind pos))))


(deftest variadic-test 
  (testing "the (apply hcall ... (rest args)) API should work with a simple argument counter"
    (let [ master (repeat 5 "arg") 
           samples (for [ x (range 5)] (subsq master 0 x)) 
           exps (map #(apply count-args %) samples)
           acts (map #(apply play adapted-count-args %) samples) ]
     (is (= exps acts)))))


(deftest with-macro-test
  (testing "the (apply hcall/hfn .. should work with macro wrapper functions"
    (let [ master '(true true false true true)
           samples (for [ x (range 5)] (subsq master 0 x)) 
           exps (map #(apply recursive-and %) samples)
           acts (map #(apply play adapted-recursive-and %) samples) ]
      (is (= exps acts)))))           


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  Nested recursive calls mixed with 
;;  literals as parameters
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn power-counter
"A degenerate and convoluted decrementing exponentiation calculator.
 power-in-numbers1/2 are collections whose added sizes give the current power
 to multiply factor by. If/when coll is empty, yields factor as the result.

 The also convoluted (and recursive) counter fn is used to count the power-in-numbers
 colls, and is invoked in nested parameter position invocations alongside the factor literal.

 Intended to illustrate and test the use of hparam fn.
"
[ power-in-numbers1 factor power-in-numbers2 coll ]
  (if (empty? coll) factor
       (power-counter (rest power-in-numbers1) 
                      (* factor (+ (counter power-in-numbers1)
                                   (counter power-in-numbers2))) 
                      (rest power-in-numbers2)  
                      (rest coll))))

(defn adapted-power-counter
[ stack pos power-in-numbers1 factor power-in-numbers2 coll ]
  (if (empty? coll) (hval [stack pos] factor)
       #_(->  
            (hparam [stack pos] (rest coll))
            (hparam (rest power-in-numbers2))
            (hcall adapted-counter 1 power-in-numbers2)
            (hcall adapted-counter 1 power-in-numbers1)
            (hfn + 2)
            (hparam factor)
            (hfn * 2)
            (hparam (rest power-in-numbers1))
            (hcall adapted-power-counter 4)
            (rewind pos))
            
        #_(-> 
            (hparam [stack pos] (rest power-in-numbers1))
            (hparam factor)
            (hparam power-in-numbers1)
            (hcall adapted-counter 1)
            (hcall adapted-counter 1 power-in-numbers2)
            (hfn + 2)
            (hfn * 2)
            (hparam (rest power-in-numbers2))
            (hparam (rest coll))
            (hcall adapted-power-counter 4)
            (rewind pos))
            
            ;;;;;;;DSL macro should yield:
          (->
            (hparam [stack pos] (rest power-in-numbers1))
            (hparam factor)
            (hparam power-in-numbers1)
            (hcall (fn [arg1 arg2 arg3] (adapted-counter arg1 arg2 arg3)) 1)
            (hparam power-in-numbers2)
            (hcall (fn [arg1 arg2 arg3] (adapted-counter arg1 arg2 arg3)) 1)
            (hfn (fn [arg1 arg2] (+ arg1 arg2)) 2)
            (hfn (fn [arg1 arg2] (* arg1 arg2)) 2)
            (hparam (rest power-in-numbers2))
            (hparam (rest coll))
            (hcall (fn [arg1 arg2 arg3 arg4 arg5 arg6] (adapted-power-counter arg1 arg2 arg3 arg4 arg5 arg6)) 4)
            (rewind pos))))

(deftest nested-mix-of-literals-and-fns-asparams-test
  (testing "nested literals, funcs and recurrent calls should work as sibling parameters"
      (let [ pcolls1 (map #(range %) (range 10))
             pcolls2 (map #(range %) (range 19 30))
             colls (map #(range %) (range 5)) 

             exp (map #(power-counter %1 1 %2 %3) pcolls1 pcolls2 colls)
             act (map #(play adapted-power-counter %1 1 %2 %3) pcolls1 pcolls2 colls) ]

          (is (= exp act)))))


                    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                    ;;                                              ;;
                    ;;       Ackermann function                     ;; 
                    ;;                                              ;;
                    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;
;; According to http://en.wikipedia.org/wiki/Ackermann_function:
;;
(defn ack
[ m n ]
  (cond (= 0 m)  (inc n)
        (and (-> m (> 0)) (= 0 n)) 
                            (ack (dec m) 1)
        (and (-> m (> 0)) (-> n (> 0)))
                            (ack (dec m) (ack m (dec n)))
    :else (throw (Exception. "ack: undefined"))))


(defn xack
[ stack pos m n]
  (cond (= 0 m) (hval [stack pos] (inc n))
        (and (-> m (> 0)) (= 0 n)) 
                  (-> (hparam [stack pos] (dec m))
                     (hparam 1)
                     (hcall (fn [arg1 arg2 arg3 arg4] (xack arg1 arg2 arg3 arg4)) 2)
                     (rewind pos))
        (and (-> m (> 0)) (-> n (> 0)))
                (-> (hparam [stack pos] (dec m))
                    (hparam m)
                    (hparam (dec n))
                    (hcall (fn [arg1 arg2 arg3 arg4] (xack arg1 arg2 arg3 arg4)) 2)
                    (hcall (fn [arg1 arg2 arg3 arg4] (xack arg1 arg2 arg3 arg4)) 2)
                    (rewind pos))
    :else
        (throw (Exception. "xack: undefined")))) 


(deftest Ackermanns_function-test
    (testing "Should work with adapted Ackermanns' function"
        (let [ exp (for [x (range 4) y (range 5) ] (ack x y)) 
               act (for [x (range 4) y (range 5) ] (play xack x y)) ]
            (is (= exp act)))))

                ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                ;;                                          ;;
                ;;     McCarthy's 91 function               ;;
                ;;                                          ;;
                ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mc91 
[ n ]
   (cond (-> n (> 100)) (- n 10) 
       :else
            (mc91 (mc91 (+ n 11)))))


(defn mc91-prime
[ stack pos n ]
   (cond (-> n (> 100)) (hval [stack pos] (- n 10))
        :else
            (-> (hparam [stack pos] (+ n 11))
                (hcall (fn [arg1 arg2 arg3] (mc91-prime arg1 arg2 arg3)) 1)
                (hcall (fn [arg1 arg2 arg3] (mc91-prime arg1 arg2 arg3)) 1)
                (rewind pos))))
                

(deftest MacCarthys91_test
    (testing "Should work with adapted MacCarthy's 91 function"
        (let [ exp (for [n (range 200)] (mc91 n))
               act (for [n (range 200)] (play mc91-prime n)) ]
            (is (= exp act)))))


                ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                ;;                                          ;;
                ;;              Tak function                ;;
                ;;                                          ;;
                ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; FROM http://en.wikipedia.org/wiki/Tak_%28function%29


;; def tak( x, y, z)
;;   if y < x
;;     tak( 
;;          tak(x-1, y, z),
;;          tak(y-1, z, x),
;;          tak(z-1, x, y)
;;        )
;;   else
;;     z
;;   end
;; end

(defn tak
[x y z]
  (if (< y x)
        (tak (tak (dec x) y z)
             (tak (dec y) z x)
             (tak (dec z) x y))

        z))


(defn xtak
[ stack pos x y z ]
  (if (< y x)
      (->
        (hparam [stack pos] (dec x))
        (hparam y) (hparam z)
        (hcall xtak 3)
        (hparam (dec y))
        (hparam z)(hparam x)
        (hcall xtak 3)
        (hparam (dec z))
        (hparam x)(hparam y)
        (hcall xtak 3)
        (hcall xtak 3)
        (rewind pos))
    
      (hval [stack pos] z)))

(deftest Tak-function_test
    (testing "Adapted Tarai function should yield the same results as the original"
        (let [  X 10 Y 5 Z 8 
                exp (for [x (range X) y (range Y) z (range Z)] (tak x y z))
                act (for [x (range X) y (range Y) z (range Z)] (play xtak x y z)) ]
            (is (= exp act)))))
