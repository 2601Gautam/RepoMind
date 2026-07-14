import logo from "../../../dist/websiteLogo.png";

// Reusable spinner for loading states
// size: 'sm', 'md' (default), 'lg', 'xl' (logo variant)

export default function LoadingSpinner({ size = "md" }) {
    // Small/Medium/Large spinner
    if (size === "sm" || size === "md" || size === "lg") {
        const dimensions =
            size === "sm"
                ? "w-4 h-4 border-2"
                : size === "lg"
                ? "w-8 h-8 border-2"
                : "w-5 h-5 border-2";

        return (
            <div className="flex items-center justify-center">
                <div
                    className={`${dimensions} border-white/20 border-t-violet-400 rounded-full animate-spin`}
                />
            </div>
        );
    }

    // XL Logo Loader
    const logoSizes = {
        xl: {
            wrapper: "w-100 h-100 rounded-3xl",
            icon: "w-80 h-80",
        },
    };

    const s = logoSizes[size] || logoSizes.xl;

    return (
        <>
            <div className="relative flex items-center justify-center select-none">
    <div
        className={`
            ${s.wrapper}
            flex
            items-center
            justify-center
            animate-logo
        `}
    >
        <img
            src={logo}
            alt="Logo"
            className={`${s.icon} object-contain`}
            draggable={false}
        />
    </div>

    <style>{`
        @keyframes logo {
            0% {
                transform: scale(0.9) rotate(-6deg);
                opacity: .8;
            }

            25% {
                transform: scale(1.03) rotate(0deg);
                opacity: 1;
            }

            50% {
                transform: scale(1.08) rotate(6deg);
                opacity: 1;
            }

            75% {
                transform: scale(1.03) rotate(0deg);
                opacity: 1;
            }

            100% {
                transform: scale(0.9) rotate(-6deg);
                opacity: .8;
            }
        }

        .animate-logo {
            animation: logo 2.4s ease-in-out infinite;
        }
    `}</style>
</div>

            <style>{`
                @keyframes float {
                    0%,100% {
                        transform: translateY(0px);
                    }
                    50% {
                        transform: translateY(-10px);
                    }
                }

                @keyframes breathe {
                    0%,100% {
                        transform: scale(1);
                    }
                    50% {
                        transform: scale(1.08);
                    }
                }

                .animate-float {
                    animation: float 3s ease-in-out infinite;
                }

                .animate-breathe {
                    animation: breathe 2s ease-in-out infinite;
                }
            `}</style>
        </>
    );
}