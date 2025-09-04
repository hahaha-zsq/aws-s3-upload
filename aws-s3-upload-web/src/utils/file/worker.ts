import {ChunkFileType, createChunk} from "@/utils/file/file";

/**
 * 在工作线程worker.js中，您可以使用onmessage事件监听器来接收来自主线程的消息，并使用postMessage()方法向主线程发送消息
 * 定义了一个异步事件处理器 onmessage，它处理来自主线程的消息。当 Web Worker 接收到消息时，这个处理器会被触发，并执行相应的逻辑来处理文件分片
 * 通过异步事件处理器 onmessage 来处理消息，确保处理逻辑不会阻塞 Web Worker
 * */
onmessage = async (e: MessageEvent) => {
  const { file, CHUNK_SIZE, startChunkIndex: start, endChunkIndex: end } = e.data
	// 创建一个空数组 promises，用于存储每个分片的处理 Promise
  const promise: Promise<ChunkFileType>[] = []
  for (let i = start; i <= end; i++) {
    promise.push(createChunk(file, i, CHUNK_SIZE))
  }
	// 使用 Promise.all 确保所有分片都处理完成后再将结果发送回主线程
  const chunks = await Promise.all(promise)
	// 当所有分片处理完成之后，使用 postMessage 方法将处理后的分片数组 chunks 发送给主线程。
  postMessage(chunks)
}
