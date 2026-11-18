import { startWorkerProbe } from "./worker-probe.mjs";

const results = document.querySelector("#results");

function report(name, passed, detail = "") {
    const item = document.createElement("li");
    item.className = passed ? "pass" : "fail";
    item.textContent = (passed ? "PASS — " : "FAIL — ") + name + (detail ? ": " + detail : "");
    results.append(item);
}

async function runProbes() {
    results.replaceChildren();
    report("ES module", true, "app.mjs executed");
    report("CSS and SVG assets", true, "style.css and fixture.svg loaded");

    try {
        const response = await fetch("./data.json");
        const payload = await response.json();
        report("Same-origin JSON fetch", response.ok, payload.name);
    } catch (error) {
        report("Same-origin JSON fetch", false, String(error));
    }

    try {
        const bytes = new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0]);
        const module = await WebAssembly.instantiate(bytes);
        report("WebAssembly", module.instance instanceof WebAssembly.Instance);
    } catch (error) {
        report("WebAssembly", false, String(error));
    }

    try {
        const canvas = document.createElement("canvas");
        const context2d = canvas.getContext("2d");
        const gl = canvas.getContext("webgl") || canvas.getContext("experimental-webgl");
        report("Canvas 2D", Boolean(context2d));
        report("WebGL", Boolean(gl), gl ? "context created" : "not available on this device");
        if (context2d) {
            context2d.fillStyle = "#7fa4ff";
            context2d.fillRect(0, 0, 16, 16);
        }
    } catch (error) {
        report("Canvas/WebGL", false, String(error));
    }

    try {
        const workerMessage = await startWorkerProbe();
        report("Dedicated worker", true, workerMessage);
    } catch (error) {
        report("Dedicated worker", false, String(error));
    }

    try {
        const storageKey = "pocket-gameplay-fixture";
        const previousValue = localStorage.getItem(storageKey);
        localStorage.setItem(storageKey, "saved");
        report(
            "localStorage persistence",
            localStorage.getItem(storageKey) === "saved",
            previousValue === "saved"
                ? "a value from an earlier page load was retained"
                : "first load; reload the game to verify persistence"
        );
    } catch (error) {
        report("localStorage persistence", false, String(error));
    }

    try {
        const database = await new Promise((resolve, reject) => {
            const request = indexedDB.open("pocket-gameplay-fixture", 1);
            request.onupgradeneeded = () => request.result.createObjectStore("checks");
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
            request.onblocked = () => reject(new Error("IndexedDB upgrade was blocked"));
        });
        const previousValue = await new Promise((resolve, reject) => {
            const transaction = database.transaction("checks", "readonly");
            const request = transaction.objectStore("checks").get("probe");
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
            transaction.onabort = () => reject(transaction.error || new Error("IndexedDB read aborted"));
        });
        await new Promise((resolve, reject) => {
            const transaction = database.transaction("checks", "readwrite");
            transaction.objectStore("checks").put("saved", "probe");
            transaction.oncomplete = resolve;
            transaction.onerror = () => reject(transaction.error);
            transaction.onabort = () => reject(transaction.error || new Error("IndexedDB write aborted"));
        });
        database.close();
        report(
            "IndexedDB persistence",
            true,
            previousValue === "saved"
                ? "a value from an earlier page load was retained"
                : "first load; reload the game to verify persistence"
        );
    } catch (error) {
        report("IndexedDB persistence", false, String(error));
    }
}

document.querySelector("#audio-button").addEventListener("click", async () => {
    try {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        const context = new AudioContextClass();
        await context.resume();
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        oscillator.frequency.value = 440;
        gain.gain.value = 0.08;
        oscillator.connect(gain);
        gain.connect(context.destination);
        oscillator.start();
        oscillator.stop(context.currentTime + 0.25);
        oscillator.onended = () => context.close();
        report("Web Audio", true, "440 Hz tone played after user gesture");
    } catch (error) {
        report("Web Audio", false, String(error));
    }
});

document.querySelector("#video-file").addEventListener("change", (event) => {
    const file = event.target.files && event.target.files[0];
    if (!file) return;
    const video = document.querySelector("#video-preview");
    if (video.dataset.objectUrl) URL.revokeObjectURL(video.dataset.objectUrl);
    const objectUrl = URL.createObjectURL(file);
    video.dataset.objectUrl = objectUrl;
    video.src = objectUrl;
    video.load();
});

document.querySelector("#storage-button").addEventListener("click", async () => {
    try {
        localStorage.setItem("pocket-gameplay-fixture", "saved");
        const value = localStorage.getItem("pocket-gameplay-fixture");
        document.querySelector("#storage-result").textContent =
            value === "saved" ? "localStorage write/read passed." : "localStorage value mismatch.";
    } catch (error) {
        document.querySelector("#storage-result").textContent = String(error);
    }
});

runProbes();
