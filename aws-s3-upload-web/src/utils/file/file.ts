import SparkMD5 from 'spark-md5';

export const CHUNK_SIZE = 1024 * 1024 * 20

/**
 * 转换文件的大小带单位
 * @param size 文件大小
 * @returns string
 */
export function convertFileSizeUnit(size = 0) {
	if (size == 0) return '0 B'

	if (size < 1024) {
		return `${size} B` // 小于1KB
	} else if (size < 1024 * 1024) {
		return `${(size / 1024).toFixed(2)} KB` // 小于1MB
	} else if (size < 1024 * 1024 * 1024) {
		return `${(size / (1024 * 1024)).toFixed(2)} MB` // 小于1GB
	} else {
		return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB` // 大于等于1GB
	}
}

export interface ChunkFileType {
	blob: Blob
	start: number
	end: number
	index: number
	hash: string
}

/**
 * @description 文件切片，多线程中使用
 * @param file 要处理的文件对象
 * @param index  当前分片的索引
 * @param CHUNK_SIZE 每个分片的大小（以字节为单位）
 * @returns
 */
export function createChunk(
	file: File,
	index: number,
	CHUNK_SIZE: number,
): Promise<ChunkFileType> {
	return new Promise((resolve) => {
		// 计算当前分片的起始位置
		const start = index * CHUNK_SIZE
		// 计算当前分片的结束位置
		const end = start + CHUNK_SIZE
		// 初始化 SparkMD5 实例，用于计算 MD5 值
		const spark = new SparkMD5.ArrayBuffer()
		// 创建一个 FileReader 实例，用于读取文件分片的内容。
		const fileReader = new FileReader()
		const blob = file.slice(start, end)
		/*	事件监听器：
				1：onload: 当文件分片读取完成后触发。
				2：onerror: 当文件分片读取过程中发生错误时触发*/
		fileReader.onload = function (e) {
			if (!e.target?.result) return
			spark.append(e.target.result as ArrayBuffer) //将读取到的 ArrayBuffer 数据追加到 spark 实例中
			resolve({
				start,
				end,
				index,
				blob,
				hash: spark.end(),  //调用 spark.end() 获取最终的 MD5 哈希值
			})
		}
		// 开始读取指定的Blob或File的内容为ArrayBuffer。
		fileReader.readAsArrayBuffer(blob)
	})
}

const THREAD_COUNT = navigator.hardwareConcurrency || 4

/**
 * 将一个较大的文件分割成多个较小的部分（分片），并通过 Web Workers 并发地处理这些分片
 * */
export async function cutFile(file: File): Promise<ChunkFileType[]> {
	// 计算文件需要分割成的分片总数(向上取整)
	const chunkCount = Math.ceil(file.size / CHUNK_SIZE)
	// 计算每个 Web Worker 需要处理的分片数量(向上取整)
	const threadChunkCount = Math.ceil(chunkCount / THREAD_COUNT)
	// 初始化一个空数组 result 用于存储最终的分片数据
	const result: ChunkFileType[] = []
	// 初始化一个计数器 finishCount 用于跟踪有多少 Web Worker 完成了任务
	let finishCount = 0

	return new Promise((resolve) => {
		// 每个 Web Worker 被分配了一定范围的分片索引，即从 start 到 end（包括 start 和 end）
		for (let i = 0; i < THREAD_COUNT; i++) {
			const start = i * threadChunkCount
			let end = (i + 1) * threadChunkCount
			if (end > chunkCount) end = chunkCount

			// 创建一个线程并分配任务,这里的 worker.js 是一个独立的 JavaScript 文件，它将作为 Worker 的入口点。当创建 Worker 实例时，浏览器会将 worker.js 文件加载到一个独立的执行环境中，并开始运行它。
			/* *
			* new Worker():
			* Worker 构造函数用于创建一个新的 Web Worker。
			* 第一个参数是 Worker 脚本的 URL 或者 Blob URL。
			* 第二个参数是一个可选的配置对象，用于设置 Worker 的类型。
			* new URL('./worker.ts', import.meta.url):
			* URL 构造函数用于创建一个新的 URL 对象。
			* 第一个参数 './worker.ts' 是相对路径，指向 Worker 脚本的位置。
			* 第二个参数 import.meta.url 是当前模块的绝对 URL，用于解析相对路径。
			* {type: 'module'}:
			* 这是一个配置对象，用于指定 Worker 的类型。
			* 'module' 指示这是一个模块化的 Worker，意味着它可以使用 ES6 模块语法。
			* 这种类型的 Worker 能够加载和执行 ES6 模块脚本。
			* */
			const worker = new Worker(new URL('./worker.ts', import.meta.url), {type: 'module'})
			// 使用Worker对象的postMessage()方法向工作线程(worker.js)发送消息,将必要的数据通过 postMessage 方法发送给 Web Worker，包括原始文件、每个分片的大小 (CHUNK_SIZE) 以及该 Web Worker 应该处理的起始和结束分片索引。
			worker.postMessage({
				file,
				CHUNK_SIZE,
				startChunkIndex: start,
				endChunkIndex: end,
			})

			// 当 Web Worker 发送消息时，onmessage 事件处理器会被触发。
			worker.onmessage = (e) => {
				// 在事件处理器中，遍历从 start 到 end 的所有索引，并按照正确的顺序将接收到的数据存入 result 数组中。
				// 按顺序依次放入数组，防止多线程并发时造成结果乱序
				for (let i = start; i < end; i++) {
					result[i] = e.data[i - start]
				}
				// 完成后，终止 Web Worker 实例。
				worker.terminate()
				// 每当一个 Web Worker 完成任务，就增加 finishCount 的值。
				finishCount++
				// 当所有的 Web Worker 都完成任务时，通过调用 resolve 函数将 result 数组作为结果传递出去。
				if (finishCount == THREAD_COUNT) {
					resolve(result)
				}
			}
		}
	})

	//   const result = []
	//   for (let i = 0; i < chunkCount; i++) {
	//     const chunk = await createChunk(file, i, chunkCount)
	//     result.push(chunk)
	//   }
	//   return result
}


