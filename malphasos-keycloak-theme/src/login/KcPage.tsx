import { Suspense, lazy } from "react";
import type { ClassKey } from "keycloakify/login";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "./Template";
import "./malphasos-login.css";
const UserProfileFormFields = lazy(
    () => import("keycloakify/login/UserProfileFormFields")
);

const doMakeUserConfirmPassword = true;

export default function KcPage(props: { kcContext: KcContext }) {
    const { kcContext } = props;

    const { i18n } = useI18n({ kcContext });

    return (
        <Suspense>
            {(() => {
                switch (kcContext.pageId) {
                    default:
                        return (
                            <DefaultPage
                                kcContext={kcContext}
                                i18n={i18n}
                                classes={classes}
                                Template={Template}
                                doUseDefaultCss={false}
                                UserProfileFormFields={UserProfileFormFields}
                                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
                            />
                        );
                }
            })()}
        </Suspense>
    );
}

/*
 * Sin la hoja por defecto de Keycloak -la de PatternFly 4, azul y con su propia tipografia- y con clases
 * propias en su lugar. Es la via que Keycloakify recomienda para un diseno que no se parece al de
 * Keycloak: sobrescribir la hoja por defecto obliga a ganarle en especificidad regla a regla, y cada
 * version de Keycloak trae reglas nuevas que ganar.
 *
 * Solo se nombran las clases que los estilos usan. Las que no aparecen aqui quedan vacias, y lo que
 * necesitan lo resuelve malphasos-login.css por su identificador -#kc-form-options, #kc-info...-, que
 * son estables entre versiones porque los formularios de Keycloak los publican.
 */
const classes = {
    kcHtmlClass: "mos-html",
    kcBodyClass: "mos-body",
    kcLoginClass: "mos-pagina",
    kcHeaderClass: "mos-cabecera",
    kcFormCardClass: "mos-tarjeta",
    kcFormHeaderClass: "mos-tarjeta__cabecera",
    kcFormClass: "mos-formulario",
    kcFormGroupClass: "mos-grupo",
    kcLabelClass: "mos-etiqueta",
    kcInputClass: "mos-campo",
    kcTextareaClass: "mos-campo",
    kcInputGroup: "mos-grupo-de-campo",
    kcInputErrorMessageClass: "mos-error",
    kcInputHelperTextBeforeClass: "mos-ayuda",
    kcInputHelperTextAfterClass: "mos-ayuda",
    kcFormPasswordVisibilityButtonClass: "mos-ver-clave",
    kcFormPasswordVisibilityIconShow: "mos-icono-ojo",
    kcFormPasswordVisibilityIconHide: "mos-icono-ojo-tachado",
    kcFormSettingClass: "mos-opciones",
    kcFormOptionsWrapperClass: "mos-opciones__enlaces",
    kcFormButtonsClass: "mos-botones",
    kcButtonClass: "mos-boton",
    kcButtonPrimaryClass: "mos-boton--primario",
    kcButtonDefaultClass: "mos-boton--secundario",
    kcButtonBlockClass: "mos-boton--bloque",
    kcAlertClass: "mos-aviso",
    kcAlertTitleClass: "mos-aviso__texto",
    kcSignUpClass: "mos-pie",
    kcLocaleMainClass: "mos-idioma",
    kcLocaleListClass: "mos-idioma__lista"
} satisfies { [key in ClassKey]?: string };
