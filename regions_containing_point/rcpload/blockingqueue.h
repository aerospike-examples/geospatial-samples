/* 
 * Copyright 2015 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more
 * contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

#ifndef __blockingqueue_h
#define __blockingqueue_h		1

#include <condition_variable>
#include <mutex>
#include <queue>
#include <stdexcept>

// Blocking queue which enforces a maximum number of items.
//
// Queue can be terminated when done.  Additional pushes immediately
// return std::out_of_range.  Pops continue to work until the queue is
// drained at which point they throw std::out_of_range as well.
//
template <typename T>
class BlockingQueue
{
public:
	BlockingQueue(size_t i_maxitems)
		: m_maxitems(i_maxitems)
		, m_done(false)
		, m_nwaitpop(0)
		, m_nwaitpush(0)
	{}  

	T pop()
	{
		std::unique_lock<std::mutex> mlock(m_mutex);

		while (m_queue.empty() && !m_done) {
			++m_nwaitpop;
			m_popcond.wait(mlock);
			--m_nwaitpop;
		}

		// Remaining items have precedence over being done.
		if (!m_queue.empty()) {
			auto item = m_queue.front();
			m_queue.pop();
			if (m_nwaitpush > 0 && m_queue.size() < m_maxitems) {
				m_pushcond.notify_one();
			}
			return item;
		}

		// We must be done
		throw std::out_of_range("queue terminated");
	}

	void push(T const & i_item)
	{
		bool notepop = false;
		{
			std::unique_lock<std::mutex> mlock(m_mutex);

			while (m_queue.size() >= m_maxitems && !m_done) {
				++m_nwaitpush;
				m_pushcond.wait(mlock);
				--m_nwaitpush;
			}

			// Being done has precendence over pushing.
			if (m_done) {
				throw std::out_of_range("queue terminated");
			}

			m_queue.push(i_item);
			if (m_nwaitpop > 0) {
				notepop = true;
			}
		}
		if (notepop)
			m_popcond.notify_one();
	}

	void terminate()
	{
		{
			std::unique_lock<std::mutex> mlock(m_mutex);
			m_done = true;
		}
		m_popcond.notify_all();
		m_pushcond.notify_all();
	}
	
private:
	size_t m_maxitems;
	bool m_done;
	size_t m_nwaitpop;
	size_t m_nwaitpush;
	std::queue<T> m_queue;
	std::mutex m_mutex;
	std::condition_variable	m_popcond;
	std::condition_variable	m_pushcond;
};

// Local Variables:
// mode: C++
// End:

#endif // __blockingqueue_h
