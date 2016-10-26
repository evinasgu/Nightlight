(ns nightlight.completions
  (:require [goog.events :as events]
            [cljs.reader :refer [read-string]]
            [paren-soup.core :as ps]
            [paren-soup.dom :as psd])
  (:import goog.net.XhrIo))

(declare refresh-completions)

(defn select-completion [editor {:keys [context-before context-after start-position]} text]
  (when-let [top-level-elem (psd/get-focused-top-level)]
    (set! (.-textContent top-level-elem)
      (str context-before text context-after))
    (let [pos (+ start-position (count text))]
      (psd/set-cursor-position! top-level-elem [pos pos]))
    (->> (ps/init-state (.querySelector js/document "#content") true false)
         (ps/add-parinfer true -1 :paren)
         (ps/edit-and-refresh! editor))))

(defn display-completions [editor info completions]
  (let [event (fn [e data]
                (select-completion editor info (.-text data))
                (refresh-completions editor))]
    (.treeview (js/$ "#completions")
      (clj->js {:data (clj->js completions)
                :onNodeSelected event
                :onNodeUnselected event}))
    (if (seq completions)
      (.show (js/$ ".rightsidebar"))
      (.hide (js/$ ".rightsidebar")))))

(defn refresh-completions [editor]
  (if-let [info (psd/get-completion-info)]
    (.send XhrIo
      "/completions"
      (fn [e]
        (display-completions editor info (read-string (.. e -target getResponseText))))
      "POST"
      (pr-str info))
    (display-completions editor {} [])))

(defn completion-shortcut? [e]
  (and (= 9 (.-keyCode e))
       (not (.-shiftKey e))
       (psd/get-completion-info)
       (some-> (psd/get-focused-top-level)
               (psd/get-cursor-position true)
               set
               count
               (= 1))))

(defn init-completions [editor-atom elem]
  (events/listen (.querySelector elem "#completions") "mousedown"
    (fn [e]
      (.preventDefault e)))
  (events/listen elem "keyup"
    (fn [e]
      (when (completion-shortcut? e)
        (when-let [node (some-> (.treeview (js/$ "#completions") "getSelected") (aget 0))]
          (when-let [info (psd/get-completion-info)]
            (select-completion @editor-atom info (.-text node))
            (refresh-completions @editor-atom)))))))

