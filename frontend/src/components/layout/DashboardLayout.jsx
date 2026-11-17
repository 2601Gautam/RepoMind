import { useState } from 'react'
import Sidebar from './Sidebar'
import ActivityPanel from '../common/ActivityPanel'

export default function DashboardLayout({ children }) {
    const [sidebarOpen, setSidebarOpen] = useState(true)

    return (
        <div className="min-h-screen bg-[#080809] flex">
            <Sidebar isOpen={sidebarOpen} onToggle={() => setSidebarOpen(!sidebarOpen)} />
            <div className={`flex-1 flex flex-col min-h-screen overflow-x-hidden transition-all duration-300 ${sidebarOpen ? 'ml-60' : 'ml-[72px]'}`}>
                <ActivityPanel />
                {children}
            </div>
        </div>
    )
}
