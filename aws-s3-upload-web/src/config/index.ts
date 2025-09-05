

/**
 * 环境配置封装
 */
//定义了一个名为 ENV 的联合类型，它可以是以下四种字符串值之一：‘dev’、‘test’、‘prod’ 或 ‘mock’。
type ENV = 'dev' | 'test' | 'prod' | 'mock'
const env = (import.meta.env.MODE as ENV) || 'mock'
console.log("env", env)
const systemName = "aws-s3-upload-web"
const config = {
	dev: {
		baseApi: 'http://localhost:8888',
		websocket: 'ws://localhost:8888/websocket',
		namespace: 'dev',
	},
	test: {
		baseApi: 'http://localhost:7458',
		websocket: 'ws://localhost:8888/websocket',
		namespace: 'test',
	},
	prod: {
		baseApi: 'http://124.70.91.190:7458',
		websocket: 'ws://localhost:8888/websocket',
		namespace: 'prod',
	},
	mock: {
		baseApi: 'http://mock:7458',
		websocket: 'ws://mock:9000',
		namespace: 'mock',
	}
}

export default {
	env,
	systemName,
	...config[env]
}


