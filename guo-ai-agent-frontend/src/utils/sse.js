/**
 * 创建SSE连接
 * @param {string} url - SSE接口地址
 * @param {Object} params - 请求参数
 * @param {Function} onMessage - 消息回调函数
 * @param {Function} onError - 错误回调函数
 * @param {Function} onOpen - 连接打开回调函数
 * @param {Function} onClose - 连接关闭回调函数
 * @returns {EventSource} EventSource实例
 */
export function createSSEConnection(url, params, onMessage, onError, onOpen, onClose) {
  // 构建查询字符串
  const queryString = new URLSearchParams(params).toString()
  const fullUrl = `${url}?${queryString}`
  
  const eventSource = new EventSource(fullUrl)
  
  eventSource.onopen = () => {
    if (onOpen) {
      onOpen()
    }
  }
  
  eventSource.onmessage = (event) => {
    if (onMessage) {
      onMessage(event.data)
    }
  }
  
  eventSource.onerror = (error) => {
    // 检查连接状态
    if (eventSource.readyState === EventSource.CLOSED) {
      // 连接已关闭（正常结束）
      if (onClose) {
        onClose()
      }
      // 只有在确实是错误时才调用 onError
      // 注意：EventSource 在流正常结束时也会触发 onerror，但 readyState 是 CLOSED
      // 这里我们只在连接关闭时调用 onClose，不调用 onError
    } else if (eventSource.readyState === EventSource.CONNECTING) {
      // 正在重连，可能是网络问题
      console.warn('SSE连接重连中...')
    } else {
      // 其他错误情况
      console.error('SSE连接错误:', error)
      if (onError) {
        onError(error)
      }
    }
  }
  
  return eventSource
}

/**
 * 关闭SSE连接
 * @param {EventSource} eventSource - EventSource实例
 */
export function closeSSEConnection(eventSource) {
  if (eventSource) {
    eventSource.close()
  }
}

