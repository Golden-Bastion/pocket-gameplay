export function startWorkerProbe() {
    return new Promise((resolve, reject) => {
        const worker = new Worker("./worker.js");
        const timeout = setTimeout(() => {
            worker.terminate();
            reject(new Error("Worker timed out."));
        }, 4000);

        worker.onmessage = (event) => {
            clearTimeout(timeout);
            worker.terminate();
            resolve(event.data);
        };
        worker.onerror = (event) => {
            clearTimeout(timeout);
            worker.terminate();
            reject(new Error(event.message || "Worker failed."));
        };
        worker.postMessage("hello");
    });
}
