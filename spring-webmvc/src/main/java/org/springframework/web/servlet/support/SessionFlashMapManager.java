/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.support;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.lang.Nullable;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.util.WebUtils;

/**
 * Store and retrieve {@link FlashMap} instances to and from the HTTP session.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1.1
 */
public class SessionFlashMapManager extends AbstractFlashMapManager {

	private static final String FLASH_MAPS_SESSION_ATTRIBUTE = SessionFlashMapManager.class.getName() + ".FLASH_MAPS";


	/**
	 * 获取所有的 flashmaps
	 *
	 * Retrieves saved FlashMap instances from the HTTP session, if any.
	 */
	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	protected List<FlashMap> retrieveFlashMaps(HttpServletRequest request) {
		// 获取 session，如果 session 为 null 不创建
		HttpSession session = request.getSession(false);
		// 获取 session 中的 FLASH_MAPS_SESSION_ATTRIBUTE 属性，获取到所有的 flashmaps
		return (session != null ? (List<FlashMap>) session.getAttribute(FLASH_MAPS_SESSION_ATTRIBUTE) : null);
	}

	/**
	 * 更新 flashMap
	 *
	 * Saves the given FlashMap instances in the HTTP session.
	 */
	@Override
	protected void updateFlashMaps(List<FlashMap> flashMaps, HttpServletRequest request, HttpServletResponse response) {
		// 其实就是将属性设置到 Session 中
		WebUtils.setSessionAttribute(request, FLASH_MAPS_SESSION_ATTRIBUTE, (!flashMaps.isEmpty() ? flashMaps : null));
	}

	/**
	 * Exposes the best available session mutex.
	 * @see org.springframework.web.util.WebUtils#getSessionMutex
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 */
	@Override
	protected Object getFlashMapsMutex(HttpServletRequest request) {
		return WebUtils.getSessionMutex(request.getSession());
	}

}
