import SparkMD5 from 'spark-md5';

/*定义默认的分片大小*/
const DEFAULT_SIZE = 20 * 1024 * 1024;
/* 函数使用了Promise，它的作用是可以异步执行一段代码，并在执行结束后返回一个结果或抛出一个错误。
在这个函数中，使用Promise封装了计算MD5值的逻辑，并通过resolve和reject回调函数来返回结果或抛出错误。
在函数内部，首先获取当前时间戳startMs作为计算耗时的起点。然后，使用FileReader和File.prototype.slice方法来读取文件并按照指定的分块大小进行处理。
通过迭代读取每个分块，将其转换成ArrayBuffer，并使用SparkMD5库来计算每个分块的MD5值。
在每个分块读取完成后，会增加分块的计数器currentChunk，并判断是否还有未处理的分块。
如果还有，继续加载下一个分块；如果已经是最后一个分块，调用spark.end()方法完成MD5计算，并将结果传递给resolve函数返回。
最后，使用loadNext函数加载第一个分块，开始整个计算过程。 */

/**
 * 计算文件的MD5值。
 * @param file 要计算MD5值的文件。
 * @param chunkSize 每个分片的大小，默认为20MB。
 * @returns Promise<string> 返回计算得到的MD5值。
 */
const md5 = (file: File, chunkSize: number = DEFAULT_SIZE): Promise<string> => {
	// Promise 的构造函数接收一个参数，是函数，而且传入两个参数：resolve，reject，分别表示异步操做执行成功后的回调函数和异步操做执行失败后的回调函数。
	return new Promise((resolve, reject) => {
		// 获取开始时间，用于计算计算 MD5 的总耗时
		const startMs = new Date().getTime();
		// const blobSlice = File.prototype.slice || File.prototype.mozSlice || File.prototype.webkitSlice;
		// 使用标准的 slice 方法来获取文件的分片
		const blobSlice = File.prototype.slice
		// 计算文件需要分割成的分片总数
		const chunks = Math.ceil(file.size / chunkSize);
		// 当前正在处理的分片索引
		let currentChunk = 0;
		// 初始化 SparkMD5 实例，用于计算 MD5 值
		const spark = new SparkMD5.ArrayBuffer(); // 追加数组缓冲区。
		// 创建一个 FileReader 实例，用于读取文件分片的内容。
		const fileReader = new FileReader(); // 读取文件
		/*	事件监听器：
			1：onload: 当文件分片读取完成后触发。
			2：onerror: 当文件分片读取过程中发生错误时触发*/
		fileReader.onload = function (e) {
			spark.append(e.target?.result as ArrayBuffer);
			currentChunk++;
			if (currentChunk < chunks) {
				loadNext();
			} else {
				const md5 = spark.end(); // 完成md5的计算，返回十六进制结果。
				console.log('文件md5计算结束，总耗时：', (new Date().getTime() - startMs) / 1000, 's');
				resolve(md5);
			}
		};
		fileReader.onerror = function (e) {
			reject(e);
		};


		function loadNext() {
			console.log('当前part number：', currentChunk, '总块数：', chunks);
			const start = currentChunk * chunkSize;
			let end = start + chunkSize;
			// 如果结束位置（end）超过了文件的大小（file.size），则将结束位置设置为文件的大小
			/* 逻辑与是一种短路逻辑，如果左侧表达式为 false，则直接短路返回结果，不再运算右侧表达式。运算逻辑如下：
			第 1 步：计算第一个操作数（左侧表达式）的值。
			第 2 步：检测第一个操作数的值。如果左侧表达式的值可转换为 false（如 null、undefined、NaN、0、""、false），那么就会结束运算，直接返回第一个操作数的值。
			第 3 步：如果第一个操作数可以转换为 true，则计算第二个操作数（右侧表达式）的值。
			第 4 步：返回第二个操作数的值。 */
			end > file.size && (end = file.size);
			/* 这段代码的含义是使用FileReader对象读取指定范围内的二进制数据，并将其转换为ArrayBuffer对象。具体操作是通过调用blobSlice方法对文件进行切片，
						然后再调用readAsArrayBuffer方法将切片后的数据读取为ArrayBuffer。其中，file表示要读取的文件，start和end表示要读取的数据范围的起始和结束位置。 */
			fileReader.readAsArrayBuffer(blobSlice.call(file, start, end));
		}

		loadNext();
	});
}

export default md5;
