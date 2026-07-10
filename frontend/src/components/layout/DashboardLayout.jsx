import Sidebar from './Sidebar'

export default function DashboardLayout({ children }) {
    return (
        <div className="min-h-screen bg-[#080809] flex">
            <Sidebar />
            {/* ml-60 matches sidebar w-60 */}
            <div className="flex-1 ml-60 flex flex-col min-h-screen overflow-x-hidden">
                {children}
            </div>
        </div>
    )
}
